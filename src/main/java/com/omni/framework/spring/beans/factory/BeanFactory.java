package com.omni.framework.spring.beans.factory;

public interface BeanFactory {
    Object getBean(String name) throws Exception;
    <T> T getBean(String name, Class<T> requiredType) throws Exception;
    <T> T getBean(Class<T> requiredType) throws Exception;
    boolean containsBean(String name);
    boolean isSingleton(String name);
}