# 创建bean

## 1 入口

```
//AbstractAutowireCapableBeanFactory.java

	protected Object createBean(final String beanName, final RootBeanDefinition mbd, final Object[] args)
			throws BeanCreationException {
		//加载bean的class类
		resolveBeanClass(mbd, beanName);
		try {
			mbd.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException();
		}

		try {
			Object bean = resolveBeforeInstantiation(beanName, mbd);
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException();
		}

		//创建bean实例
		Object beanInstance = doCreateBean(beanName, mbd, args);
		return beanInstance;
	}
```

```
	//确定bean的class类型
	//如果BeanDefinition中定义了beanClass，则直接返回beanClass
	//如果未定义，则获取beanClassName，并通过classLoader加载对应的beanClass
	protected Class<?> resolveBeanClass(final RootBeanDefinition mbd, String beanName, final Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {
		try {
			if (mbd.hasBeanClass()) {
				return mbd.getBeanClass();
			}
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {
					@Override
					public Class<?> run() throws Exception {
						return doResolveBeanClass(mbd, typesToMatch);
					}
				}, getAccessControlContext());
			}
			else {
				return doResolveBeanClass(mbd, typesToMatch);
			}
		}
		catch (Exception e) {
			...
		}
	}

```

```
//AbstractBeanFactory.java

	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch) throws ClassNotFoundException {
		if (!ObjectUtils.isEmpty(typesToMatch)) {
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				if (tempClassLoader instanceof DecoratingClassLoader) {
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
				String className = mbd.getBeanClassName();
				return (className != null ? ClassUtils.forName(className, tempClassLoader) : null);
			}
		}
		return mbd.resolveBeanClass(getBeanClassLoader());
	}
```

```
//AbstractBeanDefinition.java

	public Class<?> resolveBeanClass(ClassLoader classLoader) throws ClassNotFoundException {
		String className = getBeanClassName();
		if (className == null) {
			return null;
		}
		Class<?> resolvedClass = ClassUtils.forName(className, classLoader);
		this.beanClass = resolvedClass;
		return resolvedClass;
	}
```

```
//AbstractAutowireCapableBeanFactory.java

	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					//调用InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation方法
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						//如果上面生成了代理对象，则不再进行普通bean的实例化过程，
						//因此这里需要调用BeanPostProcessor的postProcessAfterInstantiation方法来完成bean实例化的后置处理
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}
```

## 2 整体流程

**创建bean实例**，主要步骤如下：  
1. 如果是单例模式，需要先清除缓存  
2. 实例化bean，将BeanDefinition转换为BeanWrapper  
3. bean合并后的处理（autowired注解就是通过此方法实现注入类型的预解析）  
4. 依赖处理  
5. 属性填充  
6. 循环依赖检查  
7. 注册DisposableBean  
8. 完成创建并返回  

```
//AbstractAutowireCapableBeanFactory.java

	protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final Object[] args) {
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			//根据BeanDefinition创建BeanWrapper
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		final Object bean = (instanceWrapper != null ? instanceWrapper.getWrappedInstance() : null);
		Class<?> beanType = (instanceWrapper != null ? instanceWrapper.getWrappedClass() : null);

		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				mbd.postProcessed = true;
			}
		}

		//是否需要提前暴露，单例模式并且允许循环依赖并且当前bean正在创建中，
		//则将尚未完成创建的bean实例（尚未完成属性注入及后续操作）加入提前暴露的bean实例缓存中
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			//为避免循环依赖，在bean完成创建前，将bean对应的ObjectFacotry加入工厂
			addSingletonFactory(beanName, new ObjectFactory<Object>() {
				@Override
				public Object getObject() throws BeansException {
					return getEarlyBeanReference(beanName, mbd, bean);
				}
			});
		}

		Object exposedObject = bean;
		try {
			//执行属性注入
			populateBean(beanName, mbd, instanceWrapper);
			if (exposedObject != null) {
				//调用初始化方法，如init-method
				exposedObject = initializeBean(beanName, exposedObject, mbd);
			}
		}
		catch (Throwable ex) {
			...
		}

		if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			//earlySingletonReference在检测到有循环依赖的情况下不为空
			if (earlySingletonReference != null) {
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<String>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						//检测依赖
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					//bean创建后其所依赖的bean一定是已经创建的
					//actualDependentBeans不为空表示当前bean创建后其依赖的bean没有全部创建完，也就是说存在循环依赖
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException();
					}
				}
			}
		}

		// Register bean as disposable.
		try {
			//注册disposableBean
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException();
		}

		return exposedObject;
	}
```

## 3 创建BeanWrapper

```
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, Object[] args) {
		//获取bean的class类型
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException();
		}

		//如果工厂方法不为空，则使用工厂方法实例化bean
		if (mbd.getFactoryMethodName() != null)  {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			//使用构造函数进行bean的实例化，spring需要根据构造函数的参数和类型确定使用哪个构造函数进行实例化
			//这里的解析操作较为耗时，因此采用缓存机制。如果解析过，则将解析的结果添加到resolvedConstructorOrFactoryMethod属性中。
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		//已经完成过构造函数的解析工作，则直接进行bean的实例化
		if (resolved) {
			if (autowireNecessary) {
				//使用带有参数的构造函数进行构造
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				//使用默认构造函数进行构造
				return instantiateBean(beanName, mbd);
			}
		}
		//未完成构造函数的解析，则先根据参数解析构造函数，再进行实例化
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		if (ctors != null ||
				mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args))  {
			//使用带有参数的构造函数进行构造
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		//使用默认构造函数进行构造
		return instantiateBean(beanName, mbd);
	}
```

### 使用带参数的构造函数创建bean
	 * 带有参数的实例化Bean方法，主要完成的功能如下：
	 * 1. 构造函数参数的确定
	 * 	1）根据explicitArgs参数判断
	 * 	2）从缓存中获取
	 * 	3）从配置文件获取
	 * 2. 构造函数的确定
	 * 3. 根据确定的构造函数转换对应的参数类型
	 * 4. 构造函数不确定性的验证
	 * 5. 根据实例化策略以及得到的构造函数和构造函数参数实例化bean

```
//ConstructorResolver.java

	public BeanWrapper autowireConstructor(final String beanName, final RootBeanDefinition mbd,
			Constructor<?>[] chosenCtors, final Object[] explicitArgs) {

		BeanWrapperImpl bw = new BeanWrapperImpl();
		this.beanFactory.initBeanWrapper(bw);

		//定义选取的构造函数
		Constructor<?> constructorToUse = null;
		//定义选取的构造函数参数
		ArgumentsHolder argsHolderToUse = null;
		Object[] argsToUse = null;

		//如果getBean调用时指定了方法参数，则直接使用
		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		}
		//getBean方法未指定参数，则从配置文件中解析
		else {
			Object[] argsToResolve = null;
			//尝试从缓存中获取bean的构造函数以及参数
			//解析bean定义的构造函数较为耗时，因此在完成解析后会将解析后的构造函数以及参数放入BeanDefinition中的相关字段作为缓存
			//mbd.resolvedConstructorOrFactoryMethod存放的是构造函数
			//mbd.resolvedConstructorArguments存放的是处理后的构造函数参数
			//mbd.preparedConstructorArguments存放的是原生的构造函数参数（参数类型未经过处理）
			synchronized (mbd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			//如果缓存中存在
			if (argsToResolve != null) {
				//解析参数类型，将构造参数的类型转换为最终的类型
				//eg: 构造函数A(int, int),通过此方法后就会把配置中的("1", "1")转换为(1, 1)
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve);
			}
		}

		//缓存中不存在
		if (constructorToUse == null) {
			boolean autowiring = (chosenCtors != null ||
					mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_CONSTRUCTOR);
			//存放解析后的构造函数参数的值
			ConstructorArgumentValues resolvedValues = null;

			int minNrOfArgs;
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			}
			else {
				//提取配置文件中的构造函数参数
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
				resolvedValues = new ConstructorArgumentValues();
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}

			//获取bean的class类的所有构造函数
			Constructor<?>[] candidates = chosenCtors;
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
					candidates = (mbd.isNonPublicAccessAllowed() ?
							beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				}
				catch (Throwable ex) {
					throw new BeanCreationException();
				}
			}
			//排序给定的构造函数，public构造函数优先，参数数量降序
			AutowireUtils.sortConstructors(candidates);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Set<Constructor<?>> ambiguousConstructors = null;
			LinkedList<UnsatisfiedDependencyException> causes = null;

			for (int i = 0; i < candidates.length; i++) {
				Constructor<?> candidate = candidates[i];
				Class<?>[] paramTypes = candidate.getParameterTypes();

				//如果已经找到选用的构造函数，并且构造函数所需的参数个数小于配置的构造函数参数个数则终止，因为构造函数已经按照参数个数降序
				if (constructorToUse != null && argsToUse.length > paramTypes.length) {
					break;
				}
				if (paramTypes.length < minNrOfArgs) {
					continue;
				}

				ArgumentsHolder argsHolder;
				//构造函数参数从配置文件中获取的情况
				if (resolvedValues != null) {
					//有参数则直接根据值构造对应类型的参数
					try {
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, paramTypes.length);
						if (paramNames == null) {
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
								//获取参数名称
								paramNames = pnd.getParameterNames(candidate);
							}
						}
						//根据参数名称和类型创建参数容器
						argsHolder = createArgumentArray(
								beanName, mbd, resolvedValues, bw, paramTypes, paramNames, candidate, autowiring);
					}
					catch (UnsatisfiedDependencyException ex) {
						continue;
					}
				}
				//构造函数参数从getBean方法传入的情况
				else {
					//对于构造函数从getBean方法传入的情况，参数数目必须精确匹配
					if (paramTypes.length != explicitArgs.length) {
						continue;
					}
					argsHolder = new ArgumentsHolder(explicitArgs);
				}

				//检测是否有不确定性的构造函数存在，例如不同构造函数的参数为父子关系
				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				//选择最为接近的匹配作为构造函数
				if (typeDiffWeight < minTypeDiffWeight) {
					constructorToUse = candidate;
					argsHolderToUse = argsHolder;
					argsToUse = argsHolder.arguments;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousConstructors = null;
				}
				else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					if (ambiguousConstructors == null) {
						ambiguousConstructors = new LinkedHashSet<Constructor<?>>();
						ambiguousConstructors.add(constructorToUse);
					}
					ambiguousConstructors.add(candidate);
				}
			}

			if (constructorToUse == null) {
				if (causes != null) {
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				throw new BeanCreationException();
			}
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				throw new BeanCreationException();
			}

			if (explicitArgs == null) {
				//将解析的构造函数加入缓存
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}

		//确定了构造函数以及参数后，使用构造函数对bean进行实例化
		try {
			Object beanInstance;

			if (System.getSecurityManager() != null) {
				final Constructor<?> ctorToUse = constructorToUse;
				final Object[] argumentsToUse = argsToUse;
				beanInstance = AccessController.doPrivileged(new PrivilegedAction<Object>() {
					@Override
					public Object run() {
						return beanFactory.getInstantiationStrategy().instantiate(
								mbd, beanName, beanFactory, ctorToUse, argumentsToUse);
					}
				}, beanFactory.getAccessControlContext());
			}
			else {
				beanInstance = this.beanFactory.getInstantiationStrategy().instantiate(
						mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
			}

			//将构建的实例加入BeanWrapper中
			bw.setWrappedInstance(beanInstance);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via constructor failed", ex);
		}
	}
```
```
	protected BeanWrapper instantiateBean(final String beanName, final RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			final BeanFactory parent = this;
			if (System.getSecurityManager() != null) {
				beanInstance = AccessController.doPrivileged(new PrivilegedAction<Object>() {
					@Override
					public Object run() {
						return getInstantiationStrategy().instantiate(mbd, beanName, parent);
					}
				}, getAccessControlContext());
			}
			else {
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, parent);
			}
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException();
		}
	}
```

	//具体的实例化策略（默认构造函数的实例化）

```
//SimpleInstantiationStrategy.java

	@Override
	public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner) {
		//如果需要覆盖或动态替换的方法，则需要使用cglib进行动态代理，因为可以再创建代理的同事将动态方法织入类中
		//如果没有需要动态改变的方法，则直接使用反射来实例化
		if (bd.getMethodOverrides().isEmpty()) {
			Constructor<?> constructorToUse;
			synchronized (bd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse == null) {
					final Class<?> clazz = bd.getBeanClass();
					if (clazz.isInterface()) {
						throw new BeanInstantiationException();
					}
					try {
						if (System.getSecurityManager() != null) {
							constructorToUse = AccessController.doPrivileged(new PrivilegedExceptionAction<Constructor<?>>() {
								@Override
								public Constructor<?> run() throws Exception {
									return clazz.getDeclaredConstructor((Class[]) null);
								}
							});
						}
						else {
							constructorToUse =	clazz.getDeclaredConstructor((Class[]) null);
						}
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Exception ex) {
						throw new BeanInstantiationException();
					}
				}
			}
			return BeanUtils.instantiateClass(constructorToUse);
		}
		else {
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}
```

```
	protected void populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw) {
		PropertyValues pvs = mbd.getPropertyValues();
		if (bw == null) {
			...
		}

		//调用InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation()方法
		boolean continueWithPropertyPopulation = true;

		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						continueWithPropertyPopulation = false;
						break;
					}
				}
			}
		}

		//如果后处理器发出停止属性注入的命令，则终止后边的执行
		if (!continueWithPropertyPopulation) {
			return;
		}

		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
				mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

			//根据名称自动注入
			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}

			//根据类型自动注入
			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}

			pvs = newPvs;
		}

		//后置处理器是否初始化
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		//是否需要依赖检查
		boolean needsDepCheck = (mbd.getDependencyCheck() != RootBeanDefinition.DEPENDENCY_CHECK_NONE);

		if (hasInstAwareBpps || needsDepCheck) {
			PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			if (hasInstAwareBpps) {
				for (BeanPostProcessor bp : getBeanPostProcessors()) {
					if (bp instanceof InstantiationAwareBeanPostProcessor) {
						InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
						//对所有需要依赖检查的属性进行后处理
						pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvs == null) {
							return;
						}
					}
				}
			}
			if (needsDepCheck) {
				//依赖检查
				checkDependencies(beanName, mbd, filteredPds, pvs);
			}
		}

		//将属性应用到bean中
		applyPropertyValues(beanName, mbd, bw, pvs);
	}

```


```
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		//确定beanWrapper中需要依赖注入的属性
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			if (containsBean(propertyName)) {
				//初始化相关的bean
				Object bean = getBean(propertyName);
				pvs.add(propertyName, bean);
				//注册依赖
				registerDependentBean(propertyName, beanName);
			}
		}
	}
```

```
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}

		Set<String> autowiredBeanNames = new LinkedHashSet<String>(4);
		//确定beanWrapper中需要依赖注入的属性
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			try {
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				if (!Object.class.equals(pd.getPropertyType())) {
					//探测制定属性的set方法
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
					boolean eager = !PriorityOrdered.class.isAssignableFrom(bw.getWrappedClass());
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
					//解析指定beanName的属性所匹配的值，并把解析到的属性名称存储在autowiredBeanNames中，
					//当属性存在多个封装bean时，如：
					//@Autowired private List<A> aList;
					//将会找到所有匹配A类型的bean，并将其注入
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
					if (autowiredArgument != null) {
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						registerDependentBean(autowiredBeanName, beanName);
					}
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException();
			}
		}
	}
```

```
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs == null || pvs.isEmpty()) {
			return;
		}

		MutablePropertyValues mpvs = null;
		List<PropertyValue> original;

		if (System.getSecurityManager() != null) {
			if (bw instanceof BeanWrapperImpl) {
				((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
			}
		}

		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			//如果mpvs中的值已经被转换为对应的类型，那么可以直接设置到beanWrapper中
			if (mpvs.isConverted()) {
				try {
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException();
				}
			}
			original = mpvs.getPropertyValueList();
		}
		else {
			//如果pvs并不是使用MutablePropertyValues封装的类型，那么直接使用原始的属性获取方法
			original = Arrays.asList(pvs.getPropertyValues());
		}

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		//获取对应的解析器
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		List<PropertyValue> deepCopy = new ArrayList<PropertyValue>(original.size());
		boolean resolveNecessary = false;
		//遍历属性，将属性转换为对应类的对应属性的类型
		for (PropertyValue pv : original) {
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			else {
				String propertyName = pv.getName();
				Object originalValue = pv.getValue();
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				Object convertedValue = resolvedValue;
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException();
		}
	}

```
