package com.omni.framework.spring.boot;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;
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
        log.info("Omni startup time: {} ms", totalTime);
        // 打印启动图标
        printBanner();
        // 4. 从配置中获取端口和 context path，动态输出访问地址
        int port = Integer.parseInt(ConfigLoader.getProperty("server.port", "8080"));
        String contextPath = ConfigLoader.getProperty("context.path", "/omni");
        log.info("Access http://localhost:{}{}/demo/index.html", port, contextPath);

        // 5. 等待（保持主线程存活）
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            log.error("Main thread interrupted", e);
        }
    }

    private static void printBanner() {
        String banner = "\n" +
                "   ____  _     _     _     \n" +
                "  / __ \\| |   (_)   | |    \n" +
                " | |  | | |__  _  __| | __ \n" +
                " | |  | | '_ \\| |/ _` |/ _ \\\n" +
                " | |__| | |_) | | (_| |  __/\n" +
                "  \\____/|_.__/| |\\__,_|\\___|\n" +
                "               _/ |        \n" +
                "              |__/         \n" +
                ":: Omni Framework :: (v1.0)\n";
        System.out.println(banner);
    }
}