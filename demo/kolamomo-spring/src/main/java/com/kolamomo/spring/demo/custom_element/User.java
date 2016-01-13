package com.kolamomo.spring.demo.custom_element;

/**
 * 扩展Spring自定义标签配置
 *
 * 步骤如下：
 * 1. 创建自定义标签对应的JavaBean
 * 2. 定义一个xsd文件描述组件内容
 * 3. 创建一个java文件，实现BeanDefinitionParser接口，用于解析xsd文件中的定义和组件定义
 * 4. 创建一个java文件，扩展NamespaceHandlerSupport，将组件注册到Spring容器
 * 5. 编写spring.handlers和spring.schemas文件，放在META-INF目录下
 *
 * @author jiangchao
 */
public class User {
    private int id;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
