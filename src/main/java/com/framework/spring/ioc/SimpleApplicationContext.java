package com.framework.spring.ioc;


import com.framework.jdbc.JdbcTemplate;
import com.framework.mybatis.proxy.MapperProxy;

import com.framework.spring.annotation.*;
import com.framework.tomcat.util.PackageScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

public class SimpleApplicationContext {

    private final Map<Class<?>, Object> beans = new HashMap<>();
    private final List<Object> allBeans = new ArrayList<>();
    private JdbcTemplate jdbcTemplate;

    public void scanAndInit(String packagesToScan) throws Exception {
        jdbcTemplate = new JdbcTemplate();

        String[] packages = packagesToScan.split("[;, ]+");
        Set<Class<?>> allClasses = PackageScanner.getClasses(packages);

        // 1. 创建普通 Bean
        for (Class<?> clazz : allClasses) {
            if (clazz.isInterface()) continue;
            if (clazz.isAnnotationPresent(Service.class) ||
                    clazz.isAnnotationPresent(Repository.class) ||
                    clazz.isAnnotationPresent(Controller.class) ||
                    clazz.isAnnotationPresent(Component.class)) {   // 新增此行
                Object instance = clazz.getDeclaredConstructor().newInstance();
                beans.put(clazz, instance);
                allBeans.add(instance);
                System.out.println("IoC created bean: " + clazz.getName());
            }
        }

        // 2. 生成 Mapper 接口的动态代理
        for (Class<?> clazz : allClasses) {
            if (clazz.isInterface() && clazz.isAnnotationPresent(Mapper.class)) {
                MapperProxy handler = new MapperProxy(jdbcTemplate);
                Object proxy = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
                beans.put(clazz, proxy);
                allBeans.add(proxy);
                System.out.println("IoC created mapper proxy: " + clazz.getName());
            }
        }

        // 3. 注入 @Autowired
        for (Object bean : allBeans) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    field.setAccessible(true);
                    Class<?> fieldType = field.getType();
                    Object dependency = beans.get(fieldType);
                    if (dependency == null) {
                        dependency = findImplementation(fieldType);
                    }
                    if (dependency == null) {
                        throw new RuntimeException("No bean found for type: " + fieldType.getName());
                    }
                    field.set(bean, dependency);
                    System.out.println("Autowired: " + bean.getClass().getSimpleName() + " -> " + fieldType.getSimpleName());
                }
            }
        }
    }

    private Object findImplementation(Class<?> interfaceType) {
        for (Object bean : allBeans) {
            if (interfaceType.isAssignableFrom(bean.getClass())) {
                return bean;
            }
        }
        return null;
    }

    public List<Object> getBeansWithAnnotation(Class<? extends Annotation> annotationClass) {
        List<Object> result = new ArrayList<>();
        for (Object bean : allBeans) {
            if (bean.getClass().isAnnotationPresent(annotationClass)) {
                result.add(bean);
            }
        }
        return result;
    }

    public <T> T getBean(Class<T> type) {
        return (T) beans.get(type);
    }
}