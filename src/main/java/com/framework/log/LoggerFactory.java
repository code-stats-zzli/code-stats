package com.framework.log;

import com.framework.log.config.Configuration;
import com.framework.log.core.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class LoggerFactory {
    private static Configuration config;
    private static final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

    static {
        try {
            config = new Configuration();
            // 从类路径加载 log-config.xml
            config.loadFromClasspath("log-config.xml");
            System.out.println("[LoggerFactory] 成功加载配置文件 log-config.xml");
        } catch (Exception e) {
            System.err.println("[LoggerFactory] 加载配置文件失败，使用默认控制台配置。原因: " + e.getMessage());
            // 降级：使用默认控制台配置
            config = new Configuration();
            config.useDefaultConsoleConfig();
        }
    }

    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, k -> {
            Configuration.LoggerConfig cfg = config.getLoggerConfig(k);
            if (cfg == null) cfg = config.getLoggerConfig("ROOT");
            return new Logger(k, cfg);
        });
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    public static void shutdown() {
        // 关闭所有 appender（如果需要）
        if (config != null) {
            // 遍历所有 appender 并调用 close
        }
    }
}