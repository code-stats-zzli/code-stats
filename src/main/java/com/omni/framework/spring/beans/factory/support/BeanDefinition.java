package com.omni.framework.spring.beans.factory.support;

public class BeanDefinition {
    private String beanClassName;
    private Class<?> beanClass;
    private String scope = "singleton";
    private boolean lazyInit = false;
    private String initMethodName;
    private String destroyMethodName;

    public BeanDefinition(String beanClassName) {
        this.beanClassName = beanClassName;
    }

    public String getBeanClassName() { return beanClassName; }
    public void setBeanClassName(String beanClassName) { this.beanClassName = beanClassName; }
    public Class<?> getBeanClass() { return beanClass; }
    public void setBeanClass(Class<?> beanClass) { this.beanClass = beanClass; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public boolean isLazyInit() { return lazyInit; }
    public void setLazyInit(boolean lazyInit) { this.lazyInit = lazyInit; }
    public String getInitMethodName() { return initMethodName; }
    public void setInitMethodName(String initMethodName) { this.initMethodName = initMethodName; }
    public String getDestroyMethodName() { return destroyMethodName; }
    public void setDestroyMethodName(String destroyMethodName) { this.destroyMethodName = destroyMethodName; }
}