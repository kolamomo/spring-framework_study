package com.kolamomo.spring.demo.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Created by jiangchao on 16/1/13.
 */
public class MyBeanFactoryPostProcessor  implements BeanFactoryPostProcessor {
    public void postProcessBeanFactory(ConfigurableListableBeanFactory bf)
        throws BeansException {
        BeanDefinition bd = bf.getBeanDefinition("user");
        String name = (String)bd.getPropertyValues().getPropertyValue("name").getValue().toString();
        System.out.println("3 --- call MyBeanFactoryPostProcessor.postProcessBeanFactory() name=" + name + " ---");
    }
}
