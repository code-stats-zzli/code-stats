package com.omni.framework.spring.beans.factory;


import com.omni.framework.spring.beans.factory.config.BeanPostProcessor;

public interface ConfigurableBeanFactory extends BeanFactory {
    void addBeanPostProcessor(BeanPostProcessor processor);
    void destroySingletons();
}