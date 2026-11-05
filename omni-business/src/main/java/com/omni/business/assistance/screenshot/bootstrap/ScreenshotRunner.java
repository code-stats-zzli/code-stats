package com.omni.business.assistance.screenshot.bootstrap;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.boot.runner.ApplicationRunner;
import com.omni.framework.spring.config.ConfigLoader;

@Component
public class ScreenshotRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ScreenshotRunner.class);

    @Override
    public void run(String[] args) throws Exception {
        String enabled = ConfigLoader.getProperty("screenshot.assistant.enabled", "true");
        if (!"true".equalsIgnoreCase(enabled)) {
            log.info("截图助手已禁用（screenshot.assistant.enabled=false）");
            return;
        }
        // ★ 重要：Swing EDT 是非守护线程，启动后 JVM 退出时不会自动停
        //   我们已在 SpringApplication 里用 halt(0) 强制兜底，这里正常启动即可
        log.info("启动截图助手...");
        ScreenshotAssistantBootstrap.start();
    }
}