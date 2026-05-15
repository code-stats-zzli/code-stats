package com.omni.framework.spring.beans.factory.support;


import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory {

    protected Object createBean(String beanName, BeanDefinition bd) throws Exception {
        // 1. 实例化
        Object bean = instantiateBean(bd);
        // 2. 属性填充（依赖注入）
        populateBean(bean, bd);
        // 3. 初始化前置处理
        bean = applyBeanPostProcessorsBeforeInitialization(bean, beanName);
        // 4. 调用初始化方法（简单起见，先忽略 @PostConstruct）
        // 5. 初始化后置处理
        bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
        return bean;
    }

    protected Object instantiateBean(BeanDefinition bd) throws Exception {
        if (bd.getBeanClass() == null) {
            bd.setBeanClass(Class.forName(bd.getBeanClassName()));
        }
        return bd.getBeanClass().getDeclaredConstructor().newInstance();
    }

    protected void populateBean(Object bean, BeanDefinition bd) throws Exception {
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                field.setAccessible(true);
                Object dependency = getBean(field.getType());
                field.set(bean, dependency);
            }
        }
    }

    protected Object applyBeanPostProcessorsBeforeInitialization(Object bean, String beanName) throws Exception {
        for (BeanPostProcessor processor : beanPostProcessors) {
            bean = processor.postProcessBeforeInitialization(bean, beanName);
        }
        return bean;
    }

    protected Object applyBeanPostProcessorsAfterInitialization(Object bean, String beanName) throws Exception {
        for (BeanPostProcessor processor : beanPostProcessors) {
            bean = processor.postProcessAfterInitialization(bean, beanName);
        }
        return bean;
    }
}