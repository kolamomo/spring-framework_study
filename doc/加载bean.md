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
3. 对加载的bean进行实例化  
4. 对prototype模式的bean进行循环依赖检查  
5. 如果当前配置中找不到bean，并且存在parentBeanFactory，则尝试从parentBeanFactory中加载bean  
6. 获取BeanDefinition，如果bean有parentBean，则将parentBean的属性合并进来  
7. 寻找依赖，如果当前bean依赖于其他bean，则需要先加载其依赖的bean  
8. 针对不同的scope进行bean的创建  
9. 如果创建的bean实例type与所需的不一致，则进行相应的类型转换  

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
			//对于bean是beanFactory的情况进行处理，通过beanFactory获取相应的bean实例并返回
			bean =  (sharedInstance, name, beanName, null);
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
				//获取RootBeanDefinition
				//如果指定的bean是子bean的话，需要合并父bean的属性
				final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				checkMergedBeanDefinition(mbd, beanName, args);

				//如果该bean依赖于其他bean，则首先需要加载其依赖的bean
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dependsOnBean : dependsOn) {
						if (isDependent(beanName, dependsOnBean)) {
							throw new BeanCreationException();
						}
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
								// Explicitly remove instance from singleton cache: It might have been put there
								// eagerly by the creation process, to allow for circular reference resolution.
								// Also remove any beans that received a temporary reference to the bean.
								destroySingleton(beanName);
								throw ex;
							}
						}
					});
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				//实例化prototype模式的bean
				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
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

