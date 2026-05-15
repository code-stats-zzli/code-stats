package com.omni.framework.spring.boot;


import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.spring.context.support.AnnotationConfigApplicationContext;
import com.omni.framework.tomcat.Catalina;

public class SpringApplication {
    public static void run(Class<?> primarySource, String[] args) {
        // 1. 创建并刷新 ApplicationContext
        String packageToScan = primarySource.getPackage().getName();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(packageToScan);
        SpringContextHolder.setApplicationContext(context);

        // 2. 启动 Tomcat（Catalina）
        Catalina catalina = new Catalina();
        catalina.load();
        catalina.start();
        System.out.println("Tomcat started. Access http://localhost:8080/codestats/demo/index.html");

        // 3. 等待（保持主线程存活）
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}