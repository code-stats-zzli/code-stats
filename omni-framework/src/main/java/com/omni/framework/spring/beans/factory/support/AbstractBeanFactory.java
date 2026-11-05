package com.omni.framework.spring.beans.factory.support;


import com.omni.framework.spring.beans.factory.BeanFactory;
import com.omni.framework.spring.beans.factory.config.BeanPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractBeanFactory implements BeanFactory {
    public final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();
    protected final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    @Override
    public boolean containsBean(String name) {
        return singletonObjects.containsKey(name) || containsBeanDefinition(name);
    }

    protected abstract boolean containsBeanDefinition(String name);
    protected abstract BeanDefinition getBeanDefinition(String beanName);
    protected abstract Object createBean(String beanName, BeanDefinition bd) throws Exception;

    @Override
    public Object getBean(String name) throws Exception {
        Object singleton = singletonObjects.get(name);
        if (singleton != null) return singleton;

        BeanDefinition bd = getBeanDefinition(name);
        if (bd == null) throw new Exception("No bean named " + name);

        Object bean = createBean(name, bd);
        // 如果是单例则缓存
        if ("singleton".equals(bd.getScope())) {
            singletonObjects.put(name, bean);
        }
        return bean;
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) throws Exception {
        return requiredType.cast(getBean(name));
    }

    @Override
    public <T> T getBean(Class<T> requiredType) throws Exception {
        // 先遍历单例池
        for (Object bean : singletonObjects.values()) {
            if (requiredType.isAssignableFrom(bean.getClass())) {
                return requiredType.cast(bean);
            }
        }
        // 再遍历 BeanDefinition
        String[] names = getBeanDefinitionNames();
        for (String name : names) {
            BeanDefinition bd = getBeanDefinition(name);
            if (bd.getBeanClass() != null && requiredType.isAssignableFrom(bd.getBeanClass())) {
                return requiredType.cast(getBean(name));
            }
        }
        throw new Exception("No bean of type " + requiredType);
    }

    @Override
    public boolean isSingleton(String name) {
        BeanDefinition bd = getBeanDefinition(name);
        return bd != null && "singleton".equals(bd.getScope());
    }

    public void addBeanPostProcessor(BeanPostProcessor processor) {
        beanPostProcessors.add(processor);
    }

    protected abstract String[] getBeanDefinitionNames();
}