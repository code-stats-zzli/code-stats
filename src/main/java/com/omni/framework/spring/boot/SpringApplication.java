package com.omni.framework.spring.boot;

import com.omni.framework.jdbc.DataSourceUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.boot.runner.ApplicationRunner;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.context.AbstractApplicationContext;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.spring.context.support.AnnotationConfigApplicationContext;
import com.omni.framework.tomcat.Catalina;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class SpringApplication {
    private static final Logger log = LoggerFactory.getLogger(SpringApplication.class);

    public static void run(Class<?> primarySource, String[] args) {
        long startTime = System.currentTimeMillis();

        // 1. 创建并刷新 ApplicationContext
        String packageToScan = primarySource.getPackage().getName();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(packageToScan);
        SpringContextHolder.setApplicationContext(context);
        log.info("ApplicationContext initialized in {} ms", System.currentTimeMillis() - startTime);

        // 2. 启动 Tomcat
        Catalina catalina = new Catalina();
        catalina.load();
        catalina.start();

        // 3. 输出启动信息
        int port = Integer.parseInt(ConfigLoader.getProperty("server.port", "8080"));
        String contextPath = ConfigLoader.getProperty("context.path", "/omni");
        log.info("Access http://localhost:{}{}/demo/index.html", port, contextPath);
        printBanner();
        log.info("Omni startup time: {} ms", System.currentTimeMillis() - startTime);

        // 4. ★ 执行所有业务扩展点（ApplicationRunner）
        //    框架层不再硬编码 ScreenshotAssistantBootstrap 等业务逻辑
        executeApplicationRunners(context, args);

        // 5. 优雅关闭
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, stopping Tomcat...");
            try {
                catalina.stop();
                context.getBeanFactory().destroySingletons();
                DataSourceUtils.closePool();
                log.info("Catalina stopped and resources released.");
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            } finally {
                latch.countDown();
            }
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Main thread interrupted");
        }
        log.info("Application shutdown complete.");
    }

    /**
     * 收集并执行所有 ApplicationRunner。
     * 单个 Runner 失败不影响其他 Runner，也不影响主流程。
     */
    private static void executeApplicationRunners(AbstractApplicationContext context, String[] args) {
        try {
            Map<String, ApplicationRunner> runners =
                    context.getBeanFactory().getBeansOfType(ApplicationRunner.class);
            if (runners.isEmpty()) {
                log.info("未发现 ApplicationRunner 扩展点");
                return;
            }
            log.info("发现 {} 个 ApplicationRunner，依次执行...", runners.size());
            for (Map.Entry<String, ApplicationRunner> entry : runners.entrySet()) {
                String beanName = entry.getKey();
                ApplicationRunner runner = entry.getValue();
                long t0 = System.currentTimeMillis();
                try {
                    runner.run(args);
                    log.info("ApplicationRunner [{}] 执行完成，耗时 {} ms",
                            beanName, System.currentTimeMillis() - t0);
                } catch (Exception e) {
                    log.error("ApplicationRunner [{}] 执行失败", beanName, e);
                }
            }
        } catch (Exception e) {
            log.warn("收集 ApplicationRunner 失败，跳过所有扩展点", e);
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