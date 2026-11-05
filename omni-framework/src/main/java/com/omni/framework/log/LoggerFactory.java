package com.omni.framework.log;

import com.omni.framework.log.config.Configuration;
import com.omni.framework.log.core.Logger;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class LoggerFactory {
    private static Configuration config;
    private static final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

    static {
        // ★ 关键：在创建 Configuration / FileAppender 之前就把控制台切成 UTF-8
        forceUtf8Console();

        try {
            config = new Configuration();
            config.loadFromClasspath("log-config.xml");
            System.out.println("[LoggerFactory] 成功加载配置文件 log-config.xml");
        } catch (Exception e) {
            System.err.println("[LoggerFactory] 加载配置文件失败，使用默认控制台配置。原因: " + e.getMessage());
            config = new Configuration();
            config.useDefaultConsoleConfig();
        }
    }

    /**
     * 将 System.out / System.err 重新包装为 UTF-8。
     * JDK 18+ 中 stdout.encoding 默认是系统编码（GBK），
     * 仅设置 -Dfile.encoding=UTF-8 无法改变，这里在代码层强制覆盖。
     */
    private static void forceUtf8Console() {
        try {
            System.setOut(new PrintStream(
                    new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(
                    new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 兜底失败则保持原样，不影响主流程
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
    }
}