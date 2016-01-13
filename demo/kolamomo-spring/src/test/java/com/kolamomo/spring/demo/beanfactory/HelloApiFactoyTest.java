package com.kolamomo.spring.demo.beanfactory;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.kolamomo.spring.demo.factorybean.HelloApi;

/**
 * Created by jiangchao on 16/1/13.
 */
public class HelloApiFactoyTest {
    @Test
    public void testHelloApiFactory() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("factorybean/applicationContext.xml");
        HelloApi helloApi = (HelloApi)context.getBean("helloApiFactory");
        helloApi.hello();
        String helloString = (String)context.getBean("helloStringFactory");
        System.out.println(helloString);

        System.out.println(context.getBean("helloApiFactory").getClass());
        System.out.println(context.getBean("helloStringFactory").getClass());
        //在要获取的beanName前加“&”，表示获取这个factoryBean本身
        System.out.println(context.getBean("&helloApiFactory").getClass());
        System.out.println(context.getBean("&helloStringFactory").getClass());
    }
}
