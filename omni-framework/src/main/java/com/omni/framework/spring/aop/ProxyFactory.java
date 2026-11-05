package com.omni.framework.spring.aop;

import com.omni.framework.spring.aop.MonitorAspect;

import java.lang.reflect.Proxy;

/**
 * 代理工厂：为 Bean 创建动态代理，用于监控埋点
 */
public class ProxyFactory {

    public static Object createProxy(Object target, String beanName) {
        Class<?> targetClass = target.getClass();
        // 获取所有接口（JDK 动态代理要求）
        Class<?>[] interfaces = targetClass.getInterfaces();
        if (interfaces.length == 0) {
            // 如果没有接口，可以考虑使用 CGLIB，这里简单返回原对象
            return target;
        }
        // 创建代理
        return Proxy.newProxyInstance(
                targetClass.getClassLoader(),
                interfaces,
                new MonitorAspect(target, beanName)
        );
    }
}