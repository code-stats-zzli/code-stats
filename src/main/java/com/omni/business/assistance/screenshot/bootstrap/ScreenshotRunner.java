package com.omni.business.assistance.screenshot.bootstrap;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.boot.runner.ApplicationRunner;
import com.omni.framework.spring.config.ConfigLoader;

/**
 * 截图助手启动器：实现 ApplicationRunner，由框架自动发现并执行。
 * 是否启用由配置项 screenshot.assistant.enabled 控制（默认 true）。
 */
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
        log.info("启动截图助手...");
        ScreenshotAssistantBootstrap.start();
    }
}