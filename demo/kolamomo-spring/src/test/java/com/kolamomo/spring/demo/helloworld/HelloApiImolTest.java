package com.kolamomo.spring.demo.helloworld;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by jiangchao on 16/1/13.
 */
public class HelloApiImolTest {
    @Test
    public void testHelloApi() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("helloworld/applicationContext.xml");
        HelloApi helloApi = (HelloApi)context.getBean("helloApi");
        helloApi.hello();

    }


}
