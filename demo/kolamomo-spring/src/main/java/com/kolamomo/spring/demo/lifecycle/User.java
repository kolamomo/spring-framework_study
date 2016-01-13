package com.kolamomo.spring.demo.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring IoC容器中bean的生命周期
 *
 * @author jiangchao
 */
public class User implements BeanFactoryAware, BeanNameAware,
        InitializingBean, DisposableBean, ApplicationContextAware {
    private int id;
    private String name;
    private BeanFactory beanFactory;
    private String beanName;

    //spring init-method指定的方法
    public void init() {
        System.out.println("1 --- call User init-method: init() ---");
    }

    //spring destory-method指定的方法
    public void destroyMethod() {
        System.out.println("1 --- call User destroy-method: destroyMethod() ---");
    }

    public User() {
        System.out.println("1 --- call User constructor ---");
    }

    //BeanFactoryAware接口方法
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        System.out.println("2 --- call BeanFactoryAware.setBeanFactory() ---");
        this.beanFactory = beanFactory;
    }

    //BeanNameAware接口方法
    public void setBeanName(String beanName) {
        System.out.println("2 --- call BeanNameAware.setBeanName() ---");
        this.beanName = beanName;
    }

    //InitializingBean接口方法
    public void afterPropertiesSet() throws Exception {
        System.out.println("2 --- call InitializingBean.afterPropertiesSet() ---");
    }

    //Disposable接口方法
    public void destroy() throws Exception {
        System.out.println("2 --- call DisposableBean.destroy() ---");
    }

    public void setApplicationContext(ApplicationContext context) throws BeansException {
        System.out.println("2 --- call ApplicationContextAware.setApplicationContext() ---");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        System.out.println("1 --- call User set method: setName() ---");
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String toString() {
        return "user: id = " + id + ", name = " + name;
    }
}
