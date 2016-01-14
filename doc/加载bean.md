# 加载bean
## 1 入口

```
//AbstractApplicationContext.java

	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}
```

```
//AbstractBeanFactory.java

	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

```

**加载bean的整体流程**如下：  
1. 获取beanName，如果有必要，做相应转换  
2. 尝试从缓存中获取单例模式的bean实例  
3. 加载bean前的准备工作  
4. 针对不同的scope进行bean的加载  
5. 返回bean的实例，根据需要做相应转换

```
//AbstractBeanFactory.java

	protected <T> T doGetBean(
			final String name, final Class<T> requiredType, final Object[] args, boolean typeCheckOnly)
			throws BeansException {

		//获取beanName
		final String beanName = transformedBeanName(name);
		Object bean;

		//首先尝试从缓存或者提前暴露的beanFactory中获取bean实例
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			//返回对应的bean实例
			bean =  getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
			//对于prototype模式的bean，如果存在循环依赖，则报错
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			//如果beanFactory中找不到beanName并且存在parentBeanFactory，则从parentBeanFacotry中加载bean
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				String nameToLookup = originalBeanName(name);
				if (args != null) {
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else {
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
			}

			if (!typeCheckOnly) {
				markBeanAsCreated(beanName);
			}

			try {
				//获取RootBeanDefinition，如果指定的bean是子bean的话，需要合并父bean的属性
				final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				checkMergedBeanDefinition(mbd, beanName, args);

				//如果该bean依赖于其他bean，则首先需要加载其依赖的bean
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dependsOnBean : dependsOn) {
						if (isDependent(beanName, dependsOnBean)) {
							throw new BeanCreationException();
						}
						//注册bean的依赖关系，以便在destroy时先destory依赖的bean
						registerDependentBean(dependsOnBean, beanName);
						getBean(dependsOnBean);
					}
				}

				//实例化单例模式的bean
				if (mbd.isSingleton()) {
					sharedInstance = getSingleton(beanName, new ObjectFactory<Object>() {
						@Override
						public Object getObject() throws BeansException {
							try {
								//创建singleton模式的bean实例
								return createBean(beanName, mbd, args);
							}
							catch (BeansException ex) {
								destroySingleton(beanName);
								throw ex;
							}
						}
					});
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				//实例化prototype模式的bean
				else if (mbd.isPrototype()) {
					Object prototypeInstance = null;
					try {
						beforePrototypeCreation(beanName);
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						afterPrototypeCreation(beanName);
					}
					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}

				//实例化其他scope的bean
				else {
					String scopeName = mbd.getScope();
					final Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException();
					}
					try {
						Object scopedInstance = scope.get(beanName, new ObjectFactory<Object>() {
							@Override
							public Object getObject() throws BeansException {
								beforePrototypeCreation(beanName);
								try {
									return createBean(beanName, mbd, args);
								}
								finally {
									afterPrototypeCreation(beanName);
								}
							}
						});
						bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						throw new BeanCreationException();
					}
				}
			}
			catch (BeansException ex) {
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
		}

		//检查创建的bean类型是否符合需要的类型，如果不符合则进行类型转换
		if (requiredType != null && bean != null && !requiredType.isAssignableFrom(bean.getClass())) {
			try {
				return getTypeConverter().convertIfNecessary(bean, requiredType);
			}
			catch (TypeMismatchException ex) {
				throw new BeanNotOfRequiredTypeException();
			}
		}
		return (T) bean;
	}

```

### 获取beanName

```
//AbstractBeanFactory.java

	//这里传入的参数name可能是实际的beanName，也可能是别名或者FactoryBean类型的，需要对后两者的情况进行转换处理
	//对于FactoryBean，去掉其前面的“&”
	//对于别名，找到其所对应的beanName
	protected String transformedBeanName(String name) {
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

```

### 获取单例bean的实例

```
//DefaultSingletonBeanRegistry.java

	@Override
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * 获取单例的bean对象
	 * 这里依次从三个地方获取，分别为
	 * 1. singletonObjects: 完成加载的单例bean对象的缓存
	 * 2. earlySingletonObjects：尚未完成加载的bean对象的缓存
	 * 3. singletonFactories：spring在bean未创建完成前，会将创建bean的objectFactory提前暴露，一旦下一个创建的bean依赖于某个bean时，可以直接从objectFactory中创建，以解决单例bean的循环依赖问题
	 */
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		//检查单例模式的bean缓存中是否存在该bean的实例
		Object singletonObject = this.singletonObjects.get(beanName);
		//如果单例模式的bean缓存中不存在，判断这个beanName对应的bean是否正在创建的过程中
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			//这里对对象singtonObjects进行加锁
			synchronized (this.singletonObjects) {
				//检查提前暴露的bean对象的缓存
				singletonObject = this.earlySingletonObjects.get(beanName);
				if (singletonObject == null && allowEarlyReference) {
					//某些方法需要提前初始化的时候会调用addSingletonFactory方法将对应的ObjectFactory初始化策略存储在singletonFactories中
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						singletonObject = singletonFactory.getObject();
						//获取bean对象后，将对象放入提前暴露的bean缓存，并从singletonFactories中删除
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return (singletonObject != NULL_OBJECT ? singletonObject : null);
	}
	
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<String, Object>(64);

	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>(16);

	private final Map<String, Object> earlySingletonObjects = new HashMap<String, Object>(16);

```

```
//AbstractBeanFactory.java

	if (mbd.isSingleton()) {
		sharedInstance = getSingleton(beanName, new ObjectFactory<Object>() {
			@Override
			public Object getObject() throws BeansException {
				try {
					//创建singleton模式的bean实例
					return createBean(beanName, mbd, args);
				}
				catch (BeansException ex) {
					destroySingleton(beanName);
					throw ex;
				}
			}
		});
		bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
	}
```

```
//AbstractBeanFactory.java

	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, RootBeanDefinition mbd) {

		//如果传入的name是以&为前缀，但bean类型又不是factoryBean类型，则抛出异常
		if (BeanFactoryUtils.isFactoryDereference(name) && !(beanInstance instanceof FactoryBean)) {
			throw new BeanIsNotAFactoryException();
		}

		//根据bean的类型，如果bean不是factorybean则直接返回
		//如果bean类型是factoryBean类型并且传入的name包含前缀&，说明用户需要的就是工厂实例而不是工厂getObject方法对应的实例，也直接返回。
		if (!(beanInstance instanceof FactoryBean) || BeanFactoryUtils.isFactoryDereference(name)) {
			return beanInstance;
		}

		//以下处理传入的bean类型为factoryBean类型，根据factorybean来获取所需的bean实例
		Object object = null;
		if (mbd == null) {
			//先从缓存中加载bean实例
			object = getCachedObjectForFactoryBean(beanName);
		}
		if (object == null) {
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			//获取beanDefinition
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			//这里获取BeanDefinition是用户自定义的还是自动注入的
			//如果是用户自定义的，那么在生成bean实例后，还需要调用BeanPostProcessor的后置处理器
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			//从factoryBean中获取bean对象
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}
```

```
//FactoryBeanRegistrySupport.java

	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
		//对单例模式的bean进行处理
		if (factory.isSingleton() && containsSingleton(beanName)) {
			synchronized (getSingletonMutex()) {
				//先从缓存中获取
				Object object = this.factoryBeanObjectCache.get(beanName);
				if (object == null) {
					//从facotryBean中获取bean实例
					object = doGetObjectFromFactoryBean(factory, beanName);
					Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
					if (alreadyThere != null) {
						object = alreadyThere;
					}
					else {
						if (object != null && shouldPostProcess) {
							try {
								//调用objectFactory的后置处理器
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							catch (Throwable ex) {
								throw new BeanCreationException();
							}
						}
						//将获取的bean放入缓存中
						this.factoryBeanObjectCache.put(beanName, (object != null ? object : NULL_OBJECT));
					}
				}
				return (object != NULL_OBJECT ? object : null);
			}
		}
		//对其他模式的bean进行处理
		else {
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			if (object != null && shouldPostProcess) {
				try {
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException();
				}
			}
			return object;
		}
	}
```

### 加载bean前的准备

加载bean前的准备工作包括如下工作：  
1. 对prototype模式的bean进行循环依赖检查  
2. 如果当前配置中找不到bean，并且存在parentBeanFactory，则尝试从parentBeanFactory中加载bean  
3. 获取BeanDefinition，如果bean有parentBean，则将parentBean的属性合并进来  
4. 寻找依赖，如果当前bean依赖于其他bean，则需要先加载其依赖的bean  

**检查prototype模式的循环依赖**

对于singleton模式的bean，允许循环依赖。
对于prototype模式的bean，不允许循环依赖，所以，这里在创建prototype模式的bean之前，先检查这个bean是否处在创建的过程中，如果这个bean正在创建的过程中，那么说明存在循环依赖。
例如A->B, B->C, C->A; 先加载A，这是发现A依赖B，会先加载B，B又依赖A，会去加载A，这是发现A正处于创建的过程中，因此可以判断出循环依赖。

```
	//对于prototype模式的bean，如果存在循环依赖，则报错
	if (isPrototypeCurrentlyInCreation(beanName)) {
		throw new BeanCurrentlyInCreationException(beanName);
	}
```

```
//AbstractBeanFactory.java

	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}
	
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
		new NamedThreadLocal<Object>("Prototype beans currently in creation");
```

**如果当前容器没有找到beanName对应的beanDefinition并且存在parentBeanFactory，则取parentBeanFactory中加载bean**

```
	//如果beanFactory中找不到beanName并且存在parentBeanFactory，则从parentBeanFacotry中加载bean
	BeanFactory parentBeanFactory = getParentBeanFactory();
	if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
		String nameToLookup = originalBeanName(name);
		if (args != null) {
			return (T) parentBeanFactory.getBean(nameToLookup, args);
		}
		else {
			return parentBeanFactory.getBean(nameToLookup, requiredType);
		}
	}
```

**将GernericBeanDefinition转换为RootBeanDefinition**

```
//AbstactBeanFacoty.java

	//获取RootBeanDefinition，如果指定的bean是子bean的话，需要合并父bean的属性
	final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
	checkMergedBeanDefinition(mbd, beanName, args);
```

```
//AbstactBeanFacoty.java

	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null) {
			return mbd;
		}
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}
	
	protected RootBeanDefinition getMergedBeanDefinition(
		String beanName, BeanDefinition bd, BeanDefinition containingBd)
		throws BeanDefinitionStoreException {

		synchronized (this.mergedBeanDefinitions) {
			RootBeanDefinition mbd = null;
			...
			if (mbd == null) {
				//不存在parentBean，则将beanDefinition转换为RootBeanDefinition直接返回
				if (bd.getParentName() == null) {
					...
					mbd = new RootBeanDefinition(bd);
				}
				//存在parentBean，则获取parentBean的RootBeanDefinition，在合并该bean自己的beanDefinition
				else {
					BeanDefinition pbd;
					try {
						String parentBeanName = transformedBeanName(bd.getParentName());
						if (!beanName.equals(parentBeanName)) {
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						...
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException();
					}
					mbd = new RootBeanDefinition(pbd);
					mbd.overrideFrom(bd);   //合并BeanDefinition
				}
				...

				if (containingBd == null && isCacheBeanMetadata() && isBeanEligibleForMetadataCaching(beanName)) {
					this.mergedBeanDefinitions.put(beanName, mbd); //将结果保存在map中
				}
			}

			return mbd;
		}
	}
```

**寻找依赖，先加载依赖的bean**

```
	//如果该bean依赖于其他bean，则首先需要加载其依赖的bean
	String[] dependsOn = mbd.getDependsOn();
	if (dependsOn != null) {
		for (String dependsOnBean : dependsOn) {
			if (isDependent(beanName, dependsOnBean)) {
				throw new BeanCreationException();
			}
			//注册bean的依赖关系，以便在destroy时先destory依赖的bean
			registerDependentBean(dependsOnBean, beanName);
			getBean(dependsOnBean);
		}
	}
```

### 加载bean

#### 加载singleton模式的bean

```
	sharedInstance = getSingleton(beanName, new ObjectFactory<Object>() {
		@Override
		public Object getObject() throws BeansException {
			try {
				//创建singleton模式的bean实例
				return createBean(beanName, mbd, args);
			}
			catch (BeansException ex) {
				destroySingleton(beanName);
				throw ex;
			}
		}
	});
	bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
```

```
//DefaultSingletonBeanRegistry.java

	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "'beanName' must not be null");
		//对全局变量singletonObjects进行加锁
		synchronized (this.singletonObjects) {
			//先从缓存中加载bean实例
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException();
				}
				//调用单例模式bean实例的前置处理器，将beanName记录到singletonsCurrentlyInCreation中
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				...
				try {
					//从objectFactory中获取bean实例
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					...
					throw ex;
				}
				finally {
					//创建单例模式的bean实例的后置处理器，将beanName从singletonsCurrentlyInCreation移除
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return (singletonObject != NULL_OBJECT ? singletonObject : null);
		}
	}

```

```
//DefaultSingletonRegistry.java

	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}
	
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, (singletonObject != null ? singletonObject : NULL_OBJECT));
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}
```

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
			//为避免循环依赖，在bean初始化完成前，将创建实例的ObjectFacotry加入工厂
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
			//这里的解析操作较为耗时，因此采用缓存机制。
			//如果解析过，则将解析的结果添加到resolvedConstructorOrFactoryMethod属性中。
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


