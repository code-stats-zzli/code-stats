package com.omni.framework.spring.beans.factory.support;


import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;

import java.util.LinkedHashMap;
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
        Map<String, T> result = new LinkedHashMap<>();
        // 先从已实例化的单例中查找
        for (Map.Entry<String, Object> entry : singletonObjects.entrySet()) {
            if (type.isAssignableFrom(entry.getValue().getClass())) {
                result.put(entry.getKey(), type.cast(entry.getValue()));
            }
        }
        // 再从未实例化的 BeanDefinition 中查找
        for (String beanName : getBeanDefinitionNames()) {
            if (result.containsKey(beanName)) continue;
            BeanDefinition bd = getBeanDefinition(beanName);
            if (bd.getBeanClass() != null && type.isAssignableFrom(bd.getBeanClass())) {
                // 为避免过早实例化，此处暂不 getBean，等真正注入时再实例化
                // 但为了返回 Bean 实例，这里需要调用 getBean 创建
                // 注意：可能导致循环依赖，但实际注入阶段已安全
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