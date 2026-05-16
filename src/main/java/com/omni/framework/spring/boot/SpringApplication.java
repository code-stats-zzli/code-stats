package com.omni.framework.spring.boot;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.spring.context.support.AnnotationConfigApplicationContext;
import com.omni.framework.tomcat.Catalina;

public class SpringApplication {
    private static final Logger log = LoggerFactory.getLogger(SpringApplication.class);

    public static void run(Class<?> primarySource, String[] args) {
        long startTime = System.currentTimeMillis();

        // 1. 创建并刷新 ApplicationContext
        long contextStart = System.currentTimeMillis();
        String packageToScan = primarySource.getPackage().getName();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(packageToScan);
        long contextEnd = System.currentTimeMillis();
        SpringContextHolder.setApplicationContext(context);
        log.info("ApplicationContext initialized in {} ms", contextEnd - contextStart);

        // 2. 启动 Tomcat（Catalina）
        long tomcatStart = System.currentTimeMillis();
        Catalina catalina = new Catalina();
        catalina.load();
        catalina.start();
        long tomcatEnd = System.currentTimeMillis();
        log.info("Tomcat started in {} ms", tomcatEnd - tomcatStart);

        // 3. 总启动耗时
        long totalTime = tomcatEnd - startTime;
        log.info("Total startup time: {} ms", totalTime);
        log.info("Access http://localhost:28080/omni/demo/index.html");

        // 4. 等待（保持主线程存活）
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            log.error("Main thread interrupted", e);
        }
    }
}