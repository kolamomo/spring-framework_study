package com.kolamomo.spring.demo.lifecycle;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.kolamomo.spring.demo.lifecycle.User;

/**
 * Created by jiangchao on 16/1/13.
 */
public class LifeCycleUserBeanTest {
    @Test
    public void testGetCustomElement() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("lifecycle/applicationContext.xml");
        User user = (User)context.getBean("user");
        System.out.println("user id = " + user.getId() + ", name = " + user.getName());
        context.destroy();
    }
}

/*
一月 13, 2016 7:36:13 下午 org.springframework.context.support.ClassPathXmlApplicationContext prepareRefresh
信息: Refreshing org.springframework.context.support.ClassPathXmlApplicationContext@5577140b: startup date [Wed Jan 13 19:36:13 CST 2016]; root of context hierarchy
一月 13, 2016 7:36:13 下午 org.springframework.beans.factory.xml.XmlBeanDefinitionReader loadBeanDefinitions
信息: Loading XML bean definitions from class path resource [lifecycle/applicationContext.xml]
3 --- call MyBeanFactoryPostProcessor.postProcessBeanFactory() name=TypedStringValue: value [kolamomo], target type [null] ---
3 --- call InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation() ---
1 --- call User constructor ---
3 --- call InstantiationAwareBeanPostProcessor.postProcessAfterInstantiation() ---
一月 13, 2016 7:36:14 下午 org.springframework.beans.factory.support.DefaultListableBeanFactory preInstantiateSingletons
信息: Pre-instantiating singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@1a968a59: defining beans [user,myInstanticationAwareBeanPostProcessor,myBeanPostProcessor,myBeanFactoryPostProcessor]; root of factory hierarchy

3 --- call InstantiationAwareBeanPostProcessor.postProcessProertyValues() ---
1 --- call User set method: setName() ---
2 --- call BeanNameAware.setBeanName() ---
2 --- call BeanFactoryAware.setBeanFactory() ---
2 --- call ApplicationContextAware.setApplicationContext() ---

3 --- call MyBeanPostProcessor.postProcessBeforeInstantiation() name=kolamomo ---
2 --- call InitializingBean.afterPropertiesSet() ---
1 --- call User init-method: init() ---
3 --- call MyBeanPostProcessor.postProcessAfterInstantiation() name=kolamomo ---

user id = 1, name = kolamomo

一月 13, 2016 7:36:14 下午 org.springframework.context.support.ClassPathXmlApplicationContext doClose
信息: Closing org.springframework.context.support.ClassPathXmlApplicationContext@5577140b: startup date [Wed Jan 13 19:36:13 CST 2016]; root of context hierarchy
一月 13, 2016 7:36:14 下午 org.springframework.beans.factory.support.DefaultListableBeanFactory destroySingletons
信息: Destroying singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@1a968a59: defining beans [user,myInstanticationAwareBeanPostProcessor,myBeanPostProcessor,myBeanFactoryPostProcessor]; root of factory hierarchy
2 --- call DisposableBean.destroy() ---
1 --- call User destroy-method: destroyMethod() ---
*/