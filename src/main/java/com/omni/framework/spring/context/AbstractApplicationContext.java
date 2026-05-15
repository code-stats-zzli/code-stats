package com.omni.framework.spring.context;


import com.omni.framework.spring.beans.factory.ConfigurableListableBeanFactory;
import com.omni.framework.spring.beans.factory.config.BeanFactoryPostProcessor;
import com.omni.framework.spring.beans.factory.config.BeanPostProcessor;

public abstract class AbstractApplicationContext {
    protected ConfigurableListableBeanFactory beanFactory;
    private long startupDate;

    public void refresh() throws Exception {
        // 1. 准备刷新
        prepareRefresh();
        // 2. 获取并刷新 BeanFactory
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
        // 3. 准备 BeanFactory（设置类加载器等）
        prepareBeanFactory(beanFactory);
        // 4. 执行 BeanFactoryPostProcessor
        invokeBeanFactoryPostProcessors(beanFactory);
        // 5. 注册 BeanPostProcessor
        registerBeanPostProcessors(beanFactory);
        // 6. 初始化 MessageSource（简单实现可跳过）
        initMessageSource();
        // 7. 初始化事件广播器（简单实现可跳过）
        initApplicationEventMulticaster();
        // 8. onRefresh 钩子（留给子类，如 Spring Boot 启动 Web 服务器）
        onRefresh();
        // 9. 注册事件监听器（简单实现可跳过）
        registerListeners();
        // 10. 实例化所有非懒加载单例
        finishBeanFactoryInitialization(beanFactory);
        // 11. 完成刷新，发布事件
        finishRefresh();
    }

    protected void prepareRefresh() {
        this.startupDate = System.currentTimeMillis();
    }

    protected ConfigurableListableBeanFactory obtainFreshBeanFactory() throws Exception {
        refreshBeanFactory();
        return this.beanFactory;
    }

    protected abstract void refreshBeanFactory() throws Exception;

    protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 可以设置类加载器等，暂时留空
    }

    protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) throws Exception {
        // 从 beanFactory 中获取所有 BeanFactoryPostProcessor 并执行
        for (BeanFactoryPostProcessor processor : beanFactory.getBeansOfType(BeanFactoryPostProcessor.class).values()) {
            processor.postProcessBeanFactory(beanFactory);
        }
    }

    protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) throws Exception {
        for (BeanPostProcessor processor : beanFactory.getBeansOfType(BeanPostProcessor.class).values()) {
            beanFactory.addBeanPostProcessor(processor);
        }
    }

    protected void initMessageSource() { /* 简单实现可不做 */ }
    protected void initApplicationEventMulticaster() { /* 简单实现可不做 */ }
    protected void onRefresh() throws Exception { /* 子类覆盖 */ }
    protected void registerListeners() { /* 简单实现可不做 */ }
    protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) throws Exception {
        beanFactory.preInstantiateSingletons();
    }
    protected void finishRefresh() {
        // 可发布 ContextRefreshedEvent
        System.out.println("ApplicationContext refreshed");
    }

    public ConfigurableListableBeanFactory getBeanFactory() {
        return beanFactory;
    }
}