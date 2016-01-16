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
2. 调用构造方法实例化bean  
3. 依赖处理  
4. 属性填充  
5. 循环依赖检查  
6. 注册DisposableBean  

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
		//未完成构造函数的解析，先查找容器中的SmartInstantiationAwareBeanPostProcessor接口，调用接口的determineCandidateConstructors获取候选的构造方法
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

### 解析候选的构造方法

未完成构造函数的解析，需要先查找容器中是否有注册SmartInstantiationAwareBeanPostProcessor  
如果有注册，则调用该接口的determineCandidateConstructors方法获取候选构造方法  
构造函数注入的注释autowired就是在这里进行解析的

```
//AbstractAutowireCapableBeanFactory.java

	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
					if (ctors != null) {
						return ctors;
					}
				}
			}
		}
		return null;
	}
```
determineCandidateConstructors()负责具体处理lookup-method和constructor两种方式的注入。  
将带有annotation注释的构造方法加入候选构造方法中。  

```
//AutowiredAnnotationBeanPostProcessor.java

	//从标记了autowired注释的构造方法中选取候选的构造方法
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, final String beanName) throws BeansException {
		//处理lookup-method方式的注入
		if (!this.lookupMethodsChecked.contains(beanName)) {
			ReflectionUtils.doWithMethods(beanClass, new ReflectionUtils.MethodCallback() {
				@Override
				public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
					Lookup lookup = method.getAnnotation(Lookup.class);
					if (lookup != null) {
						LookupOverride override = new LookupOverride(method, lookup.value());
						try {
							RootBeanDefinition mbd = (RootBeanDefinition) beanFactory.getMergedBeanDefinition(beanName);
							mbd.getMethodOverrides().addOverride(override);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException();
						}
					}
				}
			});
			this.lookupMethodsChecked.add(beanName);
		}

		Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
		if (candidateConstructors == null) {
			synchronized (this.candidateConstructorsCache) {
				candidateConstructors = this.candidateConstructorsCache.get(beanClass);
				if (candidateConstructors == null) {
					//获取beanClass所定义的全部构造方法
					Constructor<?>[] rawCandidates = beanClass.getDeclaredConstructors();
					List<Constructor<?>> candidates = new ArrayList<Constructor<?>>(rawCandidates.length);
					Constructor<?> requiredConstructor = null;
					Constructor<?> defaultConstructor = null;
					//从全部定义的构造方法中选取标记了autowired注释的构造方法
					for (Constructor<?> candidate : rawCandidates) {
						AnnotationAttributes ann = findAutowiredAnnotation(candidate);
						if (ann != null) {
							if (requiredConstructor != null) {
								throw new BeanCreationException();
							}
							if (candidate.getParameterTypes().length == 0) {
								throw new IllegalStateException();
							}
							boolean required = determineRequiredStatus(ann);
							//构造函数只允许一个autowired注释标记了required=true
							//标记了required=true时，如果自动注入不能满足，会抛出异常
							if (required) {
								if (!candidates.isEmpty()) {
									throw new BeanCreationException();
								}
								requiredConstructor = candidate;
							}
							candidates.add(candidate);
						}
						//设置无参的默认构造函数
						else if (candidate.getParameterTypes().length == 0) {
							defaultConstructor = candidate;
						}
					}
					if (!candidates.isEmpty()) {
						if (requiredConstructor == null) {
							if (defaultConstructor != null) {
								candidates.add(defaultConstructor);
							}
						}
						candidateConstructors = candidates.toArray(new Constructor<?>[candidates.size()]);
					}
					else {
						candidateConstructors = new Constructor<?>[0];
					}
					this.candidateConstructorsCache.put(beanClass, candidateConstructors);
				}
			}
		}
		return (candidateConstructors.length > 0 ? candidateConstructors : null);
	}
```
### 使用带参数的构造函数创建bean

带有参数的构造函数创建bean，主要完成的功能如下：   

**1 确定构造函数和参数**  
构造函数和参数的确定需要根据是否getBean()方法是否传入了构造函数参数来分别处理：  

1.1 如果传入了参数，则通过传入的参数选取构造函数  
1.2 如果未传入参数，则  
1.2.1 先检查beanDefinition中是否已经保存了解析的结果。   
BeanDefinition使用以下三个属性来保存解析的结果：  
mbd.resolvedConstructorOrFactoryMethod存放的是构造函数  
mbd.resolvedConstructorArguments存放的是处理后的构造函数参数  
mbd.preparedConstructorArguments存放的是原生的构造函数参数（参数类型未经过处理）  
1.2.2 如果没有保存解析的结果，则先获取配置文件中定义的构造函数参数，并根据参数选取构造函数  
1.2.3 将解析的结果加入BeanDefinition的相关属性中保存结果  

**2 使用选定的构造函数和参数创建bean**

```
//ConstructorResolver.java

	public BeanWrapper autowireConstructor(final String beanName, final RootBeanDefinition mbd,
			Constructor<?>[] chosenCtors, final Object[] explicitArgs) {

		BeanWrapperImpl bw = new BeanWrapperImpl();
		this.beanFactory.initBeanWrapper(bw);
		
		Constructor<?> constructorToUse = null;  //定义选取的构造函数
		ArgumentsHolder argsHolderToUse = null;  //定义选取的构造函数参数
		Object[] argsToUse = null;

		//如果getBean调用时指定了方法参数，则直接使用
		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		}
		//getBean方法未指定参数，则从配置文件中解析
		else {
			Object[] argsToResolve = null;
			//尝试从缓存中获取bean的构造函数以及参数
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

			Constructor<?>[] candidates = chosenCtors;
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
					//如果候选的构造方法为空，则获取bean的class类的所有构造函数进行选取
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
				throw new BeanCreationException();
			}
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				throw new BeanCreationException();
			}

			if (explicitArgs == null) {
				//将解析的构造函数以及参数加入缓存
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
//SimpleInstantiationStrategy.java

public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner,
			final Constructor<?> ctor, Object... args) {

		if (bd.getMethodOverrides().isEmpty()) {
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged(new PrivilegedAction<Object>() {
					@Override
					public Object run() {
						ReflectionUtils.makeAccessible(ctor);
						return null;
					}
				});
			}
			return BeanUtils.instantiateClass(ctor, args);
		}
		else {
			return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
		}
	}
```

instantiateClass()方法，使用反射调用构造函数，返回bean对象  

```
//BeanUtils.java

	public static <T> T instantiateClass(Constructor<T> ctor, Object... args) throws BeanInstantiationException {
		Assert.notNull(ctor, "Constructor must not be null");
		try {
			ReflectionUtils.makeAccessible(ctor);
			return ctor.newInstance(args);
		}
		catch (Exception ex) {
			...
		}
	}

```

### 使用默认的构造函数创建bean

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

具体的实例化策略（默认构造函数的实例化）

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

## 4 属性注入

```
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
```

```
//AbstractAutowireCapableBeanFactory.java

	protected void populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw) {
		PropertyValues pvs = mbd.getPropertyValues();
		if (bw == null) {
			...
		}

		boolean continueWithPropertyPopulation = true;
		
		//调用InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation()方法
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
					//解析指定beanName的属性所匹配的值，并把解析到的属性名称存储在autowiredBeanNames中
					//当属性存在多个封装bean时，（如：@Autowired private List<A> aList)，将会找到所有匹配A类型的bean，并将其注入
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

registerDependentBean()方法用于注册依赖，这里使用两个map来保存依赖关系：
dependentBeanMap保存的是bean和依赖它的bean集合
dependenciesForBean保存的是bean和它所依赖的bean集合

```
	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans != null && dependentBeans.contains(dependentBeanName)) {
			return;
		}

		synchronized (this.dependentBeanMap) {
			dependentBeans = this.dependentBeanMap.get(canonicalName);
			if (dependentBeans == null) {
				dependentBeans = new LinkedHashSet<String>(8);
				this.dependentBeanMap.put(canonicalName, dependentBeans);
			}
			dependentBeans.add(dependentBeanName);
		}
		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(dependentBeanName);
			if (dependenciesForBean == null) {
				dependenciesForBean = new LinkedHashSet<String>(8);
				this.dependenciesForBeanMap.put(dependentBeanName, dependenciesForBean);
			}
			dependenciesForBean.add(canonicalName);
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

```
//AbstractAutowireCapableBeanFactory.java

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

		//这里调用bean中定义的set方法为bean注入属性
		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException();
		}
	}

```

```
//AbstractAutowireCapableBeanFactory.java

	protected Object initializeBean(final String beanName, final Object bean, RootBeanDefinition mbd) {
		invokeAwareMethods(beanName, bean);	

		Object wrappedBean = bean;
		if (mbd == null || !mbd.isSynthetic()) {
			//调用后处理器beanPostProcessor的postProcessBeforeInitialization()方法
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		try {
			//调用用户自定义的init方法
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}

		if (mbd == null || !mbd.isSynthetic()) {
			//调用后处理器beanPostProcessor的postProcessAfterInitialization()方法
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}
		return wrappedBean;
	}

```

invokeAwareMethods()对特殊的bean进行处理：BeanNameAware、BeanClassLoaderAware、BeanFactoryAware

```
	private void invokeAwareMethods(final String beanName, final Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				((BeanClassLoaderAware) bean).setBeanClassLoader(getBeanClassLoader());
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}
```

```
//AbstractAutowireCapableBeanFactory.java

	protected void invokeInitMethods(String beanName, final Object bean, RootBeanDefinition mbd)
			throws Throwable {
		//检查是否实现了initializingBean接口，如果是的话，需要调用afterPropertiesSet()方法
		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
			((InitializingBean) bean).afterPropertiesSet();
		}

		if (mbd != null) {
			String initMethodName = mbd.getInitMethodName();
			if (initMethodName != null && !(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.isExternallyManagedInitMethod(initMethodName)) {
				//调用用户自定义的初始化方法
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}
```

```
protected void invokeCustomInitMethod(String beanName, final Object bean, RootBeanDefinition mbd) throws Throwable {
		String initMethodName = mbd.getInitMethodName();
		final Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));
		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Couldn't find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				return;
			}
		}

		try {
			ReflectionUtils.makeAccessible(initMethod);
			initMethod.invoke(bean);
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}
```
## 5 依赖处理

1 在加载bean的分析中，对于循环依赖的情况：  
1.1 如果是prototype模式的bean，会抛出异常  
1.2 如果是singleton模式的bean，对于构造函数注入产生的循环依赖，会抛出异常；对于set方法注入产生的循环依赖，会在调用构造函数创建bean之后，设置属性之前，将bean对应的ObjectFactory加入工厂提前暴露。  

2 设置属性的处理过程中，会设置bean的依赖关系  
在使用autowireByName/ByType加载属性之后，真正调用set方法之前，会调用checkDependencies()方法检测set方法的属性是否都加载了。  

3 在完成属性注入和init-method后，还要针对提前暴露的bean进行处理，从缓存中清理掉所有依赖于它的bean。  


```
//AbstractAutowireCapableBeanFactory.java

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
	...

	populateBean() {
		autowireByName/Type() {
			registerDependentBean(propertyName, beanName);
		}
		checkDependencies(beanName, mbd, filteredPds, pvs);
	}

	...
	if (earlySingletonExposure) {
		Object earlySingletonReference = getSingleton(beanName, false);
		//earlySingletonReference在检测到有循环依赖的情况下不为空
		if (earlySingletonReference != null) {
			if (exposedObject == bean) {
				exposedObject = earlySingletonReference;
			}
			else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
				//获取所有依赖于当前bean的beanName
				String[] dependentBeans = getDependentBeans(beanName);
				Set<String> actualDependentBeans = new LinkedHashSet<String>(dependentBeans.length);
				for (String dependentBean : dependentBeans) {
					//从缓存中移除依赖于当前bean的bean
					if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
						actualDependentBeans.add(dependentBean);
					}
				}
				if (!actualDependentBeans.isEmpty()) {
					throw new BeanCurrentlyInCreationException();
				}
			}
		}
	}
```

```
//AbstractAutowireCapableBeanFactory.java

	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		//获取bean配置中的dependencty-check属性
		int dependencyCheck = mbd.getDependencyCheck(); 	    //逐个判断bean的set方法中的属性，是否都已经加载了
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && !pvs.contains(pd.getName())) {
				//是否是基本类型
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == RootBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == RootBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == RootBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException();
				}
			}
		}
	}

```
## 6 注册disposableBean

```
//AbstractBeanFacotry.java

	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				//单例模式下注册需要销毁的bean，此方法中会处理实现DisposableBean的bean
				//并且对所有的bean使用DestructionAwareBeanPostProcessors处理
				registerDisposableBean(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
			else {
				// A bean with a custom scope...
				//自定义scope的处理
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException();
				}
				scope.registerDestructionCallback(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
		}
	}
```

```
//DefaultSingletonBeanRegistry.java

	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}
```