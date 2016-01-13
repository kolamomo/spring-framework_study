package com.kolamomo.spring.demo.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Created by jiangchao on 16/1/13.
 */
public class MyBeanPostProcessor implements BeanPostProcessor {
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {
        if("user".equals(beanName)) {
            User user = (User)bean;
            System.out.println("3 --- call MyBeanPostProcessor.postProcessBeforeInstantiation() name=" + user.getName() + " ---");
        }
        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        if("user".equals(beanName)) {
            User user = (User)bean;
            System.out.println("3 --- call MyBeanPostProcessor.postProcessAfterInstantiation() name=" + user.getName() + " ---");
        }
        return bean;
    }

}
