package com.omni.framework.spring.aop;


import com.omni.framework.spring.monitor.MonitorContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * AOP 拦截器：在方法调用前后记录监控数据到 ThreadLocal
 */
public class MonitorAspect implements InvocationHandler {

    private final Object target;
    private final String beanName;

    public MonitorAspect(Object target, String beanName) {
        this.target = target;
        this.beanName = beanName;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = beanName + "." + method.getName();
        long startTime = System.currentTimeMillis();
        boolean success = true;
        Throwable error = null;

        // 记录开始
        MonitorContext.startInvocation(methodName);

        try {
            Object result = method.invoke(target, args);
            return result;
        } catch (Throwable e) {
            success = false;
            error = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            // 记录结束
            MonitorContext.endInvocation(methodName, duration, success, error);
        }
    }
}