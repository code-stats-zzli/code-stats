package com.omni.framework.spring.beans.factory.support;


import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Primary;
import com.omni.framework.spring.annotation.Qualifier;
import com.omni.framework.spring.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.util.Map;

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
        System.out.println("Instantiating bean: " + bd.getBeanClass().getName());
        return bd.getBeanClass().getDeclaredConstructor().newInstance();
    }
    protected void populateBean(Object bean, BeanDefinition bd) throws Exception {
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                field.setAccessible(true);
                Object dependency = resolveDependency(field, bean);
                field.set(bean, dependency);
            }
        }
    }

    protected Object resolveDependency(Field field, Object bean) throws Exception {
        Class<?> requiredType = field.getType();
        // 获取 @Qualifier 值（如有）
        String qualifierValue = null;
        Qualifier qualifier = field.getAnnotation(Qualifier.class);
        if (qualifier != null && !qualifier.value().isEmpty()) {
            qualifierValue = qualifier.value();
        }
        // 获取所有候选 Bean
        Map<String, ?> candidates = this.getBeansOfType(requiredType);
        if (candidates.isEmpty()) {
            throw new Exception("No bean found for type: " + requiredType.getName());
        }
        if (candidates.size() == 1) {
            return candidates.values().iterator().next();
        }
        // 多个候选，尝试按 qualifier 或字段名匹配
        String targetBeanName = qualifierValue;
        if (targetBeanName == null) {
            targetBeanName = field.getName();   // 使用字段名作为 Bean 名称
        }
        if (candidates.containsKey(targetBeanName)) {
            return candidates.get(targetBeanName);
        }
        // 查找是否有 @Primary 标记的 Bean
        for (Map.Entry<String, ?> entry : candidates.entrySet()) {
            Object candidateBean = entry.getValue();
            if (candidateBean.getClass().isAnnotationPresent(Primary.class)) {
                return candidateBean;
            }
        }
        throw new Exception("Multiple beans found for type " + requiredType.getName() +
                ", candidates: " + candidates.keySet() + ". Please use @Qualifier or @Primary.");
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