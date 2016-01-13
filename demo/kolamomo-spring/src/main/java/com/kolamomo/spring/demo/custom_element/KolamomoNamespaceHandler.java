package com.kolamomo.spring.demo.custom_element;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * Created by jiangchao on 16/1/13.
 */
public class KolamomoNamespaceHandler extends NamespaceHandlerSupport {
    public void init() {
        registerBeanDefinitionParser("user", new UserBeanDefinitionParser());
    }
}
