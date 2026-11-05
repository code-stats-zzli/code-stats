package com.omni.framework.spring.beans.factory.config;


import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;

@FunctionalInterface
public interface BeanFactoryPostProcessor {
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws Exception;
}