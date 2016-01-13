package com.kolamomo.spring.demo.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;

import java.beans.PropertyDescriptor;

/**
 * Created by jiangchao on 16/1/13.
 */
public class MyInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {
    //InstantiationAwareBeanPostProcessor接口方法，在实例化bean前调用
    public Object postProcessBeforeInstantiation(Class beanClass, String beanName)
            throws BeansException {
        //仅针对容器中的bean：user进行调用
        if("user".equals(beanName)) {
            System.out.println("3 --- call InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation() ---");
        }
        return null;
    }

    //InstantiationAwareBeanPostProcessor接口方法，在实例化bean后调用
    public boolean postProcessAfterInstantiation(Object bean, String beanName)
            throws BeansException {
        //仅针对容器中的bean：user进行调用
        if("user".equals(beanName)) {
            System.out.println("3 --- call InstantiationAwareBeanPostProcessor.postProcessAfterInstantiation() ---");
        }
        return true;
    }

    //InstantiationAwareBeanPostProcessor接口方法，在设置某个属性时调用
    public PropertyValues postProcessPropertyValues(
            PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName)
            throws BeansException {
        if("user".equals(beanName)) {
            System.out.println("3 --- call InstantiationAwareBeanPostProcessor.postProcessProertyValues() ---");
        }
        return pvs;
    }

}
