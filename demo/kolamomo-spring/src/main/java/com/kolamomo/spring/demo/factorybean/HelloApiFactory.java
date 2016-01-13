package com.kolamomo.spring.demo.factorybean;

import org.springframework.beans.factory.FactoryBean;

/**
 * Spring FactoryBean Demo
 *
 * <p>自定义FactoryBean的步骤如下：
 * 1. implement接口FactoryBean
 * 2. 实现以下三个方法：getObject(), getObjectType(), isSingleton()
 *
 * @author jiangchao
 */

public class HelloApiFactory implements FactoryBean {
    //这里根据参数api的不同，生成不同类型的对象
    private String api;

    public void setApi(String api)  {
        this.api = api;
    }

    public Object getObject() throws Exception {
        if("hello".equals(api)) {
            return new HelloApi();
        } else {
            return new String("hello world");
        }
    }

    public Class getObjectType() {
        return "hello".equals(api) ? HelloApi.class : String.class;
    }

    public boolean isSingleton() {
        return true;
    }
}
