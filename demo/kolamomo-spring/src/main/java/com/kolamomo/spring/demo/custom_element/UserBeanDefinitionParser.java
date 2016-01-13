package com.kolamomo.spring.demo.custom_element;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;

import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;


/**
 * Created by jiangchao on 16/1/13.
 */
public class UserBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {
    protected Class getBeanClass(Element element) {
        return User.class;
    }

    protected void doParse(Element element, BeanDefinitionBuilder bean) {
        String id = element.getAttribute("id");
        String name = element.getAttribute("name");
        if(StringUtils.hasText(id)) {
            bean.addPropertyValue("id", Integer.valueOf(id));
        }
        if(StringUtils.hasText(name)) {
            bean.addPropertyValue("name", name);
        }
    }
}
