package com.omni.framework.spring.boot.runner;

/**
 * 应用启动扩展点：由业务层实现，在 Tomcat 启动完成后自动执行。
 * <p>框架层不再硬编码任何业务启动逻辑，所有扩展通过此接口注入。</p>
 * <p>执行顺序：按 {@link org.springframework.core.Ordered} 风格（本框架无 Ordered，暂按 beanName 字典序）。</p>
 */
@FunctionalInterface
public interface ApplicationRunner {
    void run(String[] args) throws Exception;
}