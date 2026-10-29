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
import java.util.concurrent.TimeUnit;

public class SpringApplication {
    private static final Logger log = LoggerFactory.getLogger(SpringApplication.class);

    public static void run(Class<?> primarySource, String[] args) {
        long startTime = System.currentTimeMillis();

        String packageToScan = primarySource.getPackage().getName();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(packageToScan);
        SpringContextHolder.setApplicationContext(context);
        log.info("ApplicationContext initialized in {} ms", System.currentTimeMillis() - startTime);

        Catalina catalina = new Catalina();
        catalina.load();
        catalina.start();

        int port = Integer.parseInt(ConfigLoader.getProperty("server.port", "8080"));
        String contextPath = ConfigLoader.getProperty("context.path", "/omni");
        log.info("Access http://localhost:{}{}/demo/index.html", port, contextPath);
        printBanner();
        log.info("Omni startup time: {} ms", System.currentTimeMillis() - startTime);

        executeApplicationRunners(context, args);

        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, stopping Tomcat...");
            try {
                catalina.stop();
            } catch (Throwable e) {
                log.error("Error stopping catalina", e);
            }
            try {
                context.getBeanFactory().destroySingletons();
            } catch (Throwable e) {
                log.error("Error destroying singletons", e);
            }
            try {
                DataSourceUtils.closePool();
            } catch (Throwable e) {
                log.error("Error closing pool", e);
            }
            log.info("Catalina stopped and resources released.");
            latch.countDown();
        }, "spring-shutdown"));

        try {
            // ★ 修复：加超时——某个 hook 卡死时不要无限等
            if (!latch.await(15, TimeUnit.SECONDS)) {
                log.warn("Shutdown hook 超时（15s），强制退出");
                Runtime.getRuntime().halt(0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Main thread interrupted");
        }
        log.info("Application shutdown complete.");

        // ★ 修复：用 halt(0) 而非 System.exit(0)
        //   System.exit(0) 会等所有 hook 完成——如果别处某个 hook 卡死，就会永远卡住
        //   halt(0) 无条件立即终止 JVM，不等任何 hook
        Runtime.getRuntime().halt(0);
    }

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