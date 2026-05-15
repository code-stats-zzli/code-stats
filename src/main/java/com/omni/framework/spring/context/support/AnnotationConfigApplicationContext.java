package com.omni.framework.spring.context.support;

import com.omni.framework.mybatis.proxy.MapperProxy;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.beans.factory.support.BeanDefinition;
import com.omni.framework.spring.beans.factory.support.DefaultListableBeanFactory;
import com.omni.framework.spring.context.AbstractApplicationContext;
import com.omni.framework.tomcat.util.PackageScanner;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Set;

public class AnnotationConfigApplicationContext extends AbstractApplicationContext {
    private final String[] basePackages;

    public AnnotationConfigApplicationContext(String... basePackages) {
        this.basePackages = basePackages;
        try {
            refresh();
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh context", e);
        }
    }

    @Override
    protected void refreshBeanFactory() throws Exception {
        this.beanFactory = new DefaultListableBeanFactory();
        loadBeanDefinitions();
        // 为 Mapper 接口创建代理并注册到容器（如果还没有被 createBean 处理）
        registerMappers();
    }

    protected void loadBeanDefinitions() throws Exception {
        Set<Class<?>> classes = PackageScanner.getClasses(basePackages);
        for (Class<?> clazz : classes) {
            if (clazz.isInterface() || clazz.isAnnotation() || Modifier.isAbstract(clazz.getModifiers())) {
                // 对于接口，如果是 Mapper 接口，也要注册（但稍后特殊处理）
                if (clazz.isInterface() && clazz.isAnnotationPresent(Mapper.class)) {
                    // 先注册一个空的 BeanDefinition，实际代理在 registerMappers 中创建
                    BeanDefinition bd = new BeanDefinition(clazz.getName());
                    bd.setBeanClass(clazz);
                    bd.setScope("singleton");
                    ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(clazz.getName(), bd);
                }
                continue;
            }
            if (isCandidateComponent(clazz)) {
                String beanName = getBeanName(clazz);
                BeanDefinition bd = new BeanDefinition(clazz.getName());
                bd.setBeanClass(clazz);
                if (clazz.isAnnotationPresent(Scope.class)) {
                    bd.setScope(clazz.getAnnotation(Scope.class).value());
                }
                ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(beanName, bd);
            }
        }
    }

    private void registerMappers() throws Exception {
        // 获取所有 Mapper 接口的 BeanDefinition，并创建代理对象
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory) beanFactory;
        for (String beanName : factory.getBeanDefinitionNames()) {
            BeanDefinition bd = factory.getBeanDefinition(beanName);
            Class<?> beanClass = bd.getBeanClass();
            if (beanClass.isInterface() && beanClass.isAnnotationPresent(Mapper.class)) {
                // 创建 Mapper 代理
                Object proxy = Proxy.newProxyInstance(
                        beanClass.getClassLoader(),
                        new Class[]{beanClass},
                        new MapperProxy(new com.omni.framework.jdbc.JdbcTemplate())
                );
                // 直接放入单例池（绕过 createBean）
                factory.singletonObjects.put(beanName, proxy);
            }
        }
    }

    private boolean isCandidateComponent(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class)
                || clazz.isAnnotationPresent(Service.class)
                || clazz.isAnnotationPresent(Repository.class)
                || clazz.isAnnotationPresent(Controller.class)
                || clazz.isAnnotationPresent(RestController.class);
    }

    private String getBeanName(Class<?> clazz) {
        Component comp = clazz.getAnnotation(Component.class);
        if (comp != null && !comp.value().isEmpty()) return comp.value();
        Service service = clazz.getAnnotation(Service.class);
        if (service != null && !service.value().isEmpty()) return service.value();
        Repository repo = clazz.getAnnotation(Repository.class);
        if (repo != null && !repo.value().isEmpty()) return repo.value();
        Controller controller = clazz.getAnnotation(Controller.class);
        if (controller != null && !controller.value().isEmpty()) return controller.value();
        RestController restController = clazz.getAnnotation(RestController.class);
        if (restController != null && !restController.value().isEmpty()) return restController.value();
        String simpleName = clazz.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}