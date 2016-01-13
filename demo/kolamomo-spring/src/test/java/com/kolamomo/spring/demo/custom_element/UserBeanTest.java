package com.kolamomo.spring.demo.custom_element;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by jiangchao on 16/1/13.
 */
public class UserBeanTest {
    @Test
    public void testGetCustomElement() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("custom_element/applicationContext.xml");
        User user = (User)context.getBean("kolamomo");
        System.out.println("user id = " + user.getId() + ", name = " + user.getName());
    }
}
