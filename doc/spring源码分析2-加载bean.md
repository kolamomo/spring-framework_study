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

		//首先尝试从缓存中获取bean实例
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
				//这里检查配置文件depends-on属性记录的依赖，构造函数参数以及属性依赖将在创建bean实例时进行检查
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

## 2 获取beanName

```
//AbstractBeanFactory.java

	//这里传入的参数name可能是实际的beanName，也可能是别名或者FactoryBean类型的，需要对后两者的情况进行转换处理
	//对于FactoryBean，去掉其前面的“&”
	//对于别名，找到其所对应的beanName
	protected String transformedBeanName(String name) {
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

```

## 3 获取Singleton模式的bean

这里依次从三个地方获取，分别为  
1. singletonObjects: 完成加载的单例bean对象的缓存
2. earlySingletonObjects：用于存储beanName->原始bean的引用，即使用工厂方法或构造方法创建出来的对象（尚未调用set以及init方法），一旦对象最终创建好，此引用信息将删除
3. singletonFactories：用于存储在beanName->原始bean的对象工厂的引用，一旦最终对象被创建(通过objectFactory.getObject())，此引用信息将删除

earlySingletonObjects和singletonFactories主要用于解决单例模式bean的循环依赖问题，在创建bean时再具体分析。

```
//DefaultSingletonBeanRegistry.java

	@Override
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

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


## 4 加载bean前的准备

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

getMergedLocalBeanDefinition()用于获取beanName对应的RootBeanDefinition。  
这里使用一个map：mergedBeanDefinitions来保存beanName对应的rootBeanDefinition，每次先从map中取，取不到再取新建，建好后再将结果保存到map中。


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
			//在mergedBeanDefinitions这个map中查找，找不到再新建
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}
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
	//这里检查配置文件depends-on属性记录的依赖，构造函数参数以及属性依赖将在创建bean实例时进行检查
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

## 5 加载bean

#### 加载singleton模式的bean

singleton模式的bean要从对象工厂ObjectFactory中进行获取。

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

获取singleton模式的bean时，需要对保存singleton模式的bean实例的map进行加锁，确保该模式的bean只会被创建一次。

```
//DefaultSingletonBeanRegistry.java

	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "'beanName' must not be null");
		//对全局变量singletonObjects进行加锁，确保singleton模式的bean只会创建一个实例
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

	//将创建好的singleton模式的bean实例加入map中，并将beanName从用于提前暴露的map中移除。
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, (singletonObject != null ? singletonObject : NULL_OBJECT));
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}
```

#### 加载prototype模式的bean

prototype模式的bean不需要像singleton模式的bean用一个map用来保存完成加载的实例，当然也就不需要加锁。
这里也需要一个map： prototypesCurrentlyInCreation 用于记录正在创建过程中的beanName

```
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

```

```
//AbstractBeanFactory.java

	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String) {
			Set<String> beanNameSet = new HashSet<String>(2);
			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}
		else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}
```

## 6 返回bean

getObjectForBeanInstance()方法对factoryBean类型的bean进行处理。

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

检查创建的bean类型是否符合需要的类型，如果不符合则进行类型转换

```
	if (requiredType != null && bean != null && !requiredType.isAssignableFrom(bean.getClass())) {
		try {
			return getTypeConverter().convertIfNecessary(bean, requiredType);
		}
		catch (TypeMismatchException ex) {
			throw new BeanNotOfRequiredTypeException();
		}
	}
```
