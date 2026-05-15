package com.omni.framework.spring.beans.factory;


import com.omni.framework.spring.beans.factory.support.BeanDefinition;

public interface ConfigurableListableBeanFactory extends ListableBeanFactory, ConfigurableBeanFactory {
    void preInstantiateSingletons() throws Exception;
    BeanDefinition getBeanDefinition(String beanName);
}