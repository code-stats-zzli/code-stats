package com.omni.framework.spring.boot;

import com.omni.business.assistance.screenshot.bootstrap.ScreenshotAssistantBootstrap;
import com.omni.framework.jdbc.DataSourceUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.spring.context.support.AnnotationConfigApplicationContext;
import com.omni.framework.tomcat.Catalina;

import java.util.concurrent.CountDownLatch;

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
        printBanner();

        // 4. 输出访问地址
        int port = Integer.parseInt(ConfigLoader.getProperty("server.port", "8080"));
        String contextPath = ConfigLoader.getProperty("context.path", "/omni");
        log.info("Access http://localhost:{}{}/demo/index.html", port, contextPath);
        ScreenshotAssistantBootstrap.start();
        // 5. 优雅关闭：等待关闭信号（Ctrl+C 或 IDEA 停止按钮）
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, stopping Tomcat...");
            try {
                catalina.stop();
                context.getBeanFactory().destroySingletons();// 关闭数据库连接池
                DataSourceUtils.closePool();
                log.info("Catalina stopped and resources released.");
                log.info("Tomcat stopped successfully.");
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            } finally {
                latch.countDown();
            }
        }));

        try {
            latch.await();  // 等待关闭信号
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Main thread interrupted");
        }
        log.info("Application shutdown complete.");


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