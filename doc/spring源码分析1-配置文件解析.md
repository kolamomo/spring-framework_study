# 配置文件的加载与解析

**spring helloworld**

在了解源码之前，先通过一个demo了解spring加载的过程。

1) 定义接口 & 实现类

接口：

```
package com.kolamomo.spring.demo.service;  
  
public interface HelloApi {  
    public void sayHello();  
}  
```
实现类：

```
package com.kolamomo.spring.demo.service;  
  
public class HelloApiImpl implements HelloApi {  
    public void sayHello(){  
        System.out.println("hello world");  
    }  
} 
``` 

2) 配置applicationContext

```
<?xml version="1.0" encoding="UTF-8"?>  
<beans xmlns="http://www.springframework.org/schema/beans"  
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"   
    xmlns:context="http://www.springframework.org/schema/context"  
    xsi:schemaLocation="    
http://www.springframework.org/schema/beans        http://www.springframework.org/schema/beans/spring-beans-3.0.xsd    
http://www.springframework.org/schema/context                http://www.springframework.org/schema/context/spring-context-3.0.xsd">  
  
    <bean id="hello" class="com.kolamomo.spring.demo.service.impl">  
    </bean>  
 </beans> 
```

3) 测试

```
package com.kolamomo.spring.demo.service;  
  
import org.junit.Test;  
import org.springframework.context.ApplicationContext;  
import org.springframework.context.support.ClassPathXmlApplicationContext;  
  
public class HelloTest {  
    @Test  
    public void testHelloWorld(){  
        ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");  
        HelloApi helloApi = context.getBean("hello", HelloApi.class);  
        helloApi.sayHello();  
}  
```

通过上面的测试类可以看出，使用spring管理配置依赖，主要分为两步：  
1. 加载spring配置文件  
2. 加载bean

这里先看spring是如果解析配置文件的。

## 1 入口
```
//ClassPathXmlApplicationContext.java

	public ClassPathXmlApplicationContext(String configLocation) throws BeansException {
		this(new String[] {configLocation}, true, null);
	}

	public ClassPathXmlApplicationContext(String[] configLocations, boolean refresh, ApplicationContext parent) throws BeansException {

		super(parent);
		setConfigLocations(configLocations);
		if (refresh) {
			refresh();  //这里开始启动配置文件的加载
		}
	}
```

```
//AbstractApplicationContext.java

	public void refresh() throws BeansException, IllegalStateException {
		//整个刷新过程是同步的
		synchronized (this.startupShutdownMonitor) {
			//做一些环境检查的准备工作
			prepareRefresh();  
			//关闭释放旧的beanFactory创建新的beanFactory，读取配置文件
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
			//对beanFactory进行一些基本的初始化
			prepareBeanFactory(beanFactory);

			try {
				postProcessBeanFactory(beanFactory);  //设置beanFactory的后置处理
				invokeBeanFactoryPostProcessors(beanFactory);  //调用beanFactory的后置处理器
				registerBeanPostProcessors(beanFactory);  //设置bean的后置处理器
				initMessageSource();  //初始化消息源
				initApplicationEventMulticaster();  //初始化上下文中的事件机制
				onRefresh();  //初始化其他特殊的bean
				registerListeners();  //注册Listeners
				finishBeanFactoryInitialization(beanFactory); //实例化bean
				finishRefresh();  //发布容器事件
			}

			catch (BeansException ex) {
				...
				throw ex;
			}
		}
	}
	
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		refreshBeanFactory();  
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		return beanFactory;
	}
```

```
//AbstractRefreshableApplicationContext.java

	protected final void refreshBeanFactory() throws BeansException {
		//如果beanFactory已存在，需要先销毁旧的beanFactory
		if (hasBeanFactory()) {
			destroyBeans();
			closeBeanFactory();
		}
		try {
			//创建一个新的beanFactory
			DefaultListableBeanFactory beanFactory = createBeanFactory();
			beanFactory.setSerializationId(getId());
			customizeBeanFactory(beanFactory);
			//加载配置文件
			loadBeanDefinitions(beanFactory);
			synchronized (this.beanFactoryMonitor) {
				this.beanFactory = beanFactory;
			}
		}
		catch (IOException ex) {
			...
		}
	}

```
	
```
//AbstractXmlApplicationContext

	protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
		//创建XmlBeanDefinitionReader用于读取配置文件
		XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

		beanDefinitionReader.setEnvironment(this.getEnvironment());
		beanDefinitionReader.setResourceLoader(this);
		beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

		initBeanDefinitionReader(beanDefinitionReader);
		loadBeanDefinitions(beanDefinitionReader);
	}
	
	protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws BeansException, IOException {
		Resource[] configResources = getConfigResources();
		if (configResources != null) {
			reader.loadBeanDefinitions(configResources);
		}
		String[] configLocations = getConfigLocations();
		if (configLocations != null) {
			reader.loadBeanDefinitions(configLocations);
		}
	}

```

## 2 加载配置文件

```
//AbstractBeanDefinitionReader.java

	public int loadBeanDefinitions(Resource... resources) throws BeanDefinitionStoreException {
		Assert.notNull(resources, "Resource array must not be null");
		int counter = 0;
		for (Resource resource : resources) {
			counter += loadBeanDefinitions(resource);
		}
		return counter;
	}
```

```
//XmlBeanDefinitionReader.java
	public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(new EncodedResource(resource));
	}

	//从资源文件中加载bean的定义信息
	public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
		Assert.notNull(encodedResource, "EncodedResource must not be null");

		//获取资源文件
		Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
		if (currentResources == null) {
			currentResources = new HashSet<EncodedResource>(4);
			this.resourcesCurrentlyBeingLoaded.set(currentResources);
		}
		if (!currentResources.add(encodedResource)) {
			throw new BeanDefinitionStoreException();
		}
		try {
			InputStream inputStream = encodedResource.getResource().getInputStream();
			try {
				InputSource inputSource = new InputSource(inputStream);
				if (encodedResource.getEncoding() != null) {
					inputSource.setEncoding(encodedResource.getEncoding());
				}
				return doLoadBeanDefinitions(inputSource, encodedResource.getResource());
			}
			finally {
				inputStream.close();
			}
		}
		catch (IOException ex) {
			...
		}
		finally {
			currentResources.remove(encodedResource);
			if (currentResources.isEmpty()) {
				this.resourcesCurrentlyBeingLoaded.remove();
			}
		}
	}
	
	protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
			throws BeanDefinitionStoreException {
		try {
			//加载配置文件，结果保存为Document对象
			Document doc = doLoadDocument(inputSource, resource);
			//解析配置Document对象，并注册BeanDefinition
			return registerBeanDefinitions(doc, resource);
		} catch (BeanDefinitionStoreException ex) {
			throw ex;
		} catch ( ) {
		} 
		...
	}
```

```
//XmlBeanDefinitionReader.java
	
		protected Document doLoadDocument(InputSource inputSource, Resource resource) throws Exception {
		return this.documentLoader.loadDocument(inputSource, getEntityResolver(), this.errorHandler,
				getValidationModeForResource(resource), isNamespaceAware());
	}


```

```
//DefaultDocumentLoader.java

	public Document loadDocument(InputSource inputSource, EntityResolver entityResolver,
			ErrorHandler errorHandler, int validationMode, boolean namespaceAware) throws Exception {

		DocumentBuilderFactory factory = createDocumentBuilderFactory(validationMode, namespaceAware);
		DocumentBuilder builder = createDocumentBuilder(factory, entityResolver, errorHandler);
		return builder.parse(inputSource);
	}

```	

```
//DocumentBuilderImpl.java

    public Document parse(InputSource is) throws SAXException, IOException {
        if (is == null) {
            throw new IllegalArgumentException( );
        }
        if (fSchemaValidator != null) {
            if (fSchemaValidationManager != null) {
                fSchemaValidationManager.reset();
                fUnparsedEntityHandler.reset();
            }
            resetSchemaValidator();
        }
        domParser.parse(is);
        Document doc = domParser.getDocument();
        domParser.dropDocumentReferences();
        return doc;
    }
```

## 3 解析配置文件


```
//XmlBeanDefinitionReader.java

	public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
		BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
		documentReader.setEnvironment(getEnvironment());
		int countBefore = getRegistry().getBeanDefinitionCount();
		documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
		return getRegistry().getBeanDefinitionCount() - countBefore;
	}

```

```
//DefaultBeanDefinitionDocumentReader.java

	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		Element root = doc.getDocumentElement();
		doRegisterBeanDefinitions(root);
	}

	protected void doRegisterBeanDefinitions(Element root) {
		BeanDefinitionParserDelegate parent = this.delegate;
		this.delegate = createDelegate(getReaderContext(), root, parent);

		//处理profile属性，如果定义了profile属性，则去环境变量中寻找
		//profile属性用于部署多套不同的配置，eg：<beans profile="dev">...</beans>
		if (this.delegate.isDefaultNamespace(root)) {
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					return;
				}
			}
		}

		preProcessXml(root);	//模板方法，内容为空，用于继承扩展
		//解析BeanDefinition
		parseBeanDefinitions(root, this.delegate);
		postProcessXml(root);	//模板方法，内容为空，用于继承扩展


		this.delegate = parent;
	}

	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		//解析默认标签
		if (delegate.isDefaultNamespace(root)) {
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if (delegate.isDefaultNamespace(ele)) {
						//解析默认标签下的默认属性
						parseDefaultElement(ele, delegate);
					}
					else {
						//解析默认标签下的自定义属性
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		//解析自定义标签
		else {
			delegate.parseCustomElement(root);
		}
	}
```

### 3.1 解析默认标签

```
//DefaultBeanDefinitionDocumentReader.java
	
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			//对import标签的处理
			importBeanDefinitionResource(ele);
		}
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			//对alias标签的处理
			processAliasRegistration(ele);
		}
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			//对bean标签的处理
			processBeanDefinition(ele, delegate);
		}
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			//对beans标签的处理，递归
			doRegisterBeanDefinitions(ele);
		}
	}
```

#### 3.1.1 解析import标签
importBeanDefinitionResource()方法负责处理import标签，定位import标签所指向的资源文件，并加载该资源文件。

```
//DefaultBeanDefinitionDocumentReader.java

	//解析import标签, eg: <import resource="spring.xml" />
	protected void importBeanDefinitionResource(Element ele) {
		//提取resource标签
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		if (!StringUtils.hasText(location)) {
			return;
		}

		//解析系统属性, e.g. "${user.dir}"
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<Resource>(4);

		//判断location是相对uri还是绝对uri
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
		}

		//绝对URI，则获取该URI对应的配置文件，并加载其对应的BeanDefinition
		if (absoluteLocation) {
			try {
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				}
			}
			catch (BeanDefinitionStoreException ex) {
			}
		}
		//相对URI
		else {
			...
		}
		Resource[] actResArray = actualResources.toArray(new Resource[actualResources.size()]);
		//完成解析后，触发监听器事件
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

```

### 3.1.2 解析alias标签

processAliasRegistration()用于处理alias别名，把结果放在aliasMap中。

```
//DefaultBeanDefinitionDocumentReader.java

	protected void processAliasRegistration(Element ele) {
		//提取name标签
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		//提取alias标签
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			valid = false;
		}
		if (valid) {
			try {
				//注册alias别名
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
			}
			//完成注册后通知监听器
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}
```

```
//SimpleAliasRregistry.java

	private final Map<String, String> aliasMap = new ConcurrentHashMap<String, String>(16);

	//注册别名，将别名注册到全局的aliasMap中
	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		//如果alias与beanName相同，则不进行注册，并删除aliasMap中已存在的list
		if (alias.equals(name)) {
			this.aliasMap.remove(alias);
		}
		else {
			//如果alias已经存在并指向了另一个不同的beanName，则抛出异常
			if (!allowAliasOverriding()) {
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null && !registeredName.equals(name)) {
					throw new IllegalStateException();
				}
			}
			//alias循环检查
			checkForAliasCircle(name, alias);
			//注册别名
			this.aliasMap.put(alias, name);
		}
	}

```

#### 3.1.3 解析bean标签

```
//DefaultBeanDefinitionDocumentReader.java

	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		//解析bean标签
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			//如果bean标签的子节点下还有自定义属性，则需要对自定义标签进行解析
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				//对解析后的bhHolder进行注册
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
			}
			//解析注册完成后，发出响应时间，通知相关的监听器
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}
```


```
//BeanDefinitionParserDelegate.java

	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele) {
		return parseBeanDefinitionElement(ele, null);
	}

	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, BeanDefinition containingBean) {
		//解析id属性
		String id = ele.getAttribute(ID_ATTRIBUTE);
		//解析name属性
		String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

		//分割name属性：
		//可以指定多个name，之间可以用分号（“；”）、空格（“ ”）或逗号（“，”）分隔开，如果没有指定id，那么第一个name为标识符，其余的为别名；若指定了id属性，则id为标识符，所有的name均为别名。
		List<String> aliases = new ArrayList<String>();
		if (StringUtils.hasLength(nameAttr)) {
			String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
			aliases.addAll(Arrays.asList(nameArr));
		}

		//默认情况下，beanName为id；若未定义id，则beanName为name属性的第一个name
		String beanName = id;
		if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
			beanName = aliases.remove(0);
		}

		//检验beanName的唯一性，使用set记录所有的beanName
		if (containingBean == null) {
			checkNameUniqueness(beanName, aliases, ele); 
		}

		//解析bean中的所有属性，将其封装到beanDefinition中
		AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
		if (beanDefinition != null) {
			if (!StringUtils.hasText(beanName)) {
				try {
					//如果未定义beanName，则根据spring的规则为当前bean生成beanName
					if (containingBean != null) {
						beanName = BeanDefinitionReaderUtils.generateBeanName(
								beanDefinition, this.readerContext.getRegistry(), true);
					}
					else {
						beanName = this.readerContext.generateBeanName(beanDefinition);
						String beanClassName = beanDefinition.getBeanClassName();
						if (beanClassName != null &&
								beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
								!this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
							aliases.add(beanClassName);
						}
					}
				}
				catch (Exception ex) {
					return null;
				}
			}
			String[] aliasesArray = StringUtils.toStringArray(aliases);
			return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
		}

		return null;
	}
```

```
public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor
		implements BeanDefinition, Cloneable {
	private volatile Object beanClass;
	//bean的作用范围，对应bean属性scope
	private String scope = SCOPE_DEFAULT;
	//是否是抽象，对应bean属性abstract
	private boolean abstractFlag = false;
	//是否要延迟加载，对应bean属性lazy-init
	private boolean lazyInit = false;
	//自动注入模式，对应bean属性autowire
	private int autowireMode = AUTOWIRE_NO;
	//依赖检查
	private int dependencyCheck = DEPENDENCY_CHECK_NONE;
	//用来表示bean的实例化依赖于另一个bean先实例化，对应bean属性depend-on
	private String[] dependsOn;
	//对应bean属性autowire-candidate，设置为false时，在自动装配时，将忽略该bean
	private boolean autowireCandidate = true;
	//对应bean属性primary，在自动装配时作为首选
	private boolean primary = false;
	//对应子元素qualifier
	private final Map<String, AutowireCandidateQualifier> qualifiers =
			new LinkedHashMap<String, AutowireCandidateQualifier>(0);
	//允许访问非公开构造器和方法
	private boolean nonPublicAccessAllowed = true;
	//是否以宽松的模式解析构造函数
	private boolean lenientConstructorResolution = true;
	//构造函数参数，对应bean属性construct-arg
	private ConstructorArgumentValues constructorArgumentValues;
	//普通属性集合，对应子元素property
	private MutablePropertyValues propertyValues;  
	//方法重写的持有者，记录lookup-method、replaced-method
	private MethodOverrides methodOverrides = new MethodOverrides();
	//对应bean属性的factory-bean
	private String factoryBeanName;
	//对应bean属性的factory-method
	private String factoryMethodName;
	//初始化方法，对应bean属性的init-method
	private String initMethodName;
	//销毁方法，对应bean属性的destroy-method
	private String destroyMethodName;
	//是否执行init-method
	private boolean enforceInitMethod = true;
	//是否执行destroy-method
	private boolean enforceDestroyMethod = true;
	//是否是用户定义的面而非应用程序本身定义的，创建AOP时为true
	private boolean synthetic = false;
	//定义bean的应用
	private int role = BeanDefinition.ROLE_APPLICATION;
	//bean的描述信息
	private String description;
	//bean定义对应的资源
	private Resource resource;
	
	...
}
```

```
public class MutablePropertyValues implements PropertyValues, Serializable {
	private final List<PropertyValue> propertyValueList;  //存储bean中定义的property
	private Set<String> processedProperties;
	private volatile boolean converted = false;
	...
}
```

```
public class BeanDefinitionHolder implements BeanMetadataElement {
	private final BeanDefinition beanDefinition;
	private final String beanName;
	private final String[] aliases;
	...
```


```
//BeanDefinitionReaderUtils.java

	public static void registerBeanDefinition(
			BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		// Register bean definition under primary name.
		//使用beanName做唯一标识，将BeanDefinition注册到BeanDefinitionRegistry中
		String beanName = definitionHolder.getBeanName();
		//见DefaultListableBeanFactory类的实现
		registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

		// Register aliases for bean name, if any.
		//注册所有的别名
		String[] aliases = definitionHolder.getAliases();
		if (aliases != null) {
			for (String alias : aliases) {
				registry.registerAlias(beanName, alias);
			}
		}
	}

```

```
//DefaultListableBeanFactory.java

	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException {

		Assert.hasText(beanName, "Bean name must not be empty");
		Assert.notNull(beanDefinition, "BeanDefinition must not be null");

		if (beanDefinition instanceof AbstractBeanDefinition) {
			try {
				//注册前的最后一次校验，对BeanDefinition中的methodOverrides进行校验
				//校验methodOverrides是否与工厂方法并存或者对应方法根本不存在
				((AbstractBeanDefinition) beanDefinition).validate();
			}
			catch (BeanDefinitionValidationException ex) {
			}
		}

		BeanDefinition oldBeanDefinition;

		oldBeanDefinition = this.beanDefinitionMap.get(beanName);
		//处理已经注册的beanName的情况
		if (oldBeanDefinition != null) {
			if (!isAllowBeanDefinitionOverriding()) {
				throw new BeanDefinitionStoreException();
			}
		}
		else {
			this.beanDefinitionNames.add(beanName);
			this.manualSingletonNames.remove(beanName);
			this.frozenBeanDefinitionNames = null;
		}
		//注册beanDefinition，将beanDefinition放入全局的beanDefinitionMap中
		this.beanDefinitionMap.put(beanName, beanDefinition);

		if (oldBeanDefinition != null || containsSingleton(beanName)) {
			//重置所有beanName对应的缓存
			resetBeanDefinition(beanName);
		}
	}

```

```
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
		implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

	private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories =
			new ConcurrentHashMap<String, Reference<DefaultListableBeanFactory>>(8);

	private String serializationId;

	private boolean allowBeanDefinitionOverriding = true;

	private boolean allowEagerClassLoading = true;

	private Comparator<Object> dependencyComparator;

	private AutowireCandidateResolver autowireCandidateResolver = new SimpleAutowireCandidateResolver();

	private final Map<Class<?>, Object> resolvableDependencies = new HashMap<Class<?>, Object>(16);

	private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<String, BeanDefinition>(64);

	private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<Class<?>, String[]>(64);

	private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<Class<?>, String[]>(64);

	private final List<String> beanDefinitionNames = new ArrayList<String>(64);

	private final Set<String> manualSingletonNames = new LinkedHashSet<String>(16);

	private boolean configurationFrozen = false;

	private String[] frozenBeanDefinitionNames;

```

### 3.2 解析自定义标签

扩展spring自定义标签配置需要以下几个步骤：  
创建一个需要扩展的组件  
定义一个xsd文件描述组件内容     
创建一个java文件，实现BeanDefinitionParser接口，用于解析xsd文件中的定义和组件定义  
创建一个java文件，扩展NamespaceHandlerSupport，将组件注册到Spring容器  
编写Spring.handlers和Spring.schemas文件  


```
//BeanDefinitionParserDelegate.java

	public BeanDefinition parseCustomElement(Element ele) {
		return parseCustomElement(ele, null);
	}

	public BeanDefinition parseCustomElement(Element ele, BeanDefinition containingBd) {
		//获取标签对应的命名空间
		String namespaceUri = getNamespaceURI(ele);
		//根据命名空间找到对应的namespaceHandler
		NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
		if (handler == null) {
			return null;
		}
		//调用自定义的namespaceHandler进行解析
		return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd));
	}
```

```
//NamespaceHandlerSupport.java

	public BeanDefinition parse(Element element, ParserContext parserContext) {
		//寻找解析器，并进行解析
		return findParserForElement(element, parserContext).parse(element, parserContext);
	}

	private BeanDefinitionParser findParserForElement(Element element, ParserContext parserContext) {
		//获取元素名称,eg: <weibo:mc id="" name="" /> 这里元素名称为mc
		String localName = parserContext.getDelegate().getLocalName(element);
		//根据元素名称找到对应的解析器，即在registerBeanDefinitionParser("mc", new WeiboMcBeanDefinitionParser()); 这里注册的解析器
		BeanDefinitionParser parser = this.parsers.get(localName);
		if (parser == null) {
			parserContext.getReaderContext().fatal(
					"Cannot locate BeanDefinitionParser for element [" + localName + "]", element);
		}
		return parser;
	}

```
	
```	
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		//设置beanFactory的classLoader为当前context的classLoader
		beanFactory.setBeanClassLoader(getClassLoader());
		//设置beanFactory的表达式语言处理器
		beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		//为beanFactory增加了一个默认的propertyEditor
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		//添加处理aware相关接口的beanPostProcessor扩展
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		//设置几个忽略自动装配的接口
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);

		//设置了几个自动装配的特殊规则
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// Detect a LoadTimeWeaver and prepare for weaving, if found.
		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching.
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// Register default environment beans.
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
	}

```

