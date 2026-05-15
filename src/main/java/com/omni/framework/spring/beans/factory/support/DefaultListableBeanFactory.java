package com.omni.framework.spring.beans.factory.support;


import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
        implements ConfigurableListableBeanFactory, BeanDefinitionRegistry {

    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        beanDefinitionMap.put(beanName, beanDefinition);
    }

    @Override
    public void removeBeanDefinition(String beanName) {
        beanDefinitionMap.remove(beanName);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
        return beanDefinitionMap.get(beanName);
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return beanDefinitionMap.containsKey(beanName);
    }

    @Override
    public String[] getBeanDefinitionNames() {
        return beanDefinitionMap.keySet().toArray(new String[0]);
    }

    @Override
    public void preInstantiateSingletons() throws Exception {
        for (String beanName : getBeanDefinitionNames()) {
            BeanDefinition bd = getBeanDefinition(beanName);
            if ("singleton".equals(bd.getScope()) && !bd.isLazyInit()) {
                getBean(beanName);
            }
        }
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type) throws Exception {
        Map<String, T> result = new ConcurrentHashMap<>();
        for (String beanName : getBeanDefinitionNames()) {
            BeanDefinition bd = getBeanDefinition(beanName);
            if (bd.getBeanClass() != null && type.isAssignableFrom(bd.getBeanClass())) {
                result.put(beanName, type.cast(getBean(beanName)));
            }
        }
        return result;
    }

    @Override
    public void destroySingletons() {
        singletonObjects.clear();
    }
}