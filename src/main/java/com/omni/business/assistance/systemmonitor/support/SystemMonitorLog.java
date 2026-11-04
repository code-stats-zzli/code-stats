package com.omni.business.assistance.systemmonitor.support;

import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.config.ConfigLoader;

/**
 * 系统监控模块日志开关门面。
 *
 * <p>目的：{@code systemmonitor} 模块被前端每 2 秒轮询一次
 * {@code /systemmonitor/api/all}，每次都会触发各组件采集。部分组件在
 * 采集失败（JDK 版本差异、外部命令缺失等）时会打 warn / error，
 * 导致日志被刷屏。本类提供统一的开关，默认全关。</p>
 *
 * <p>配置项（application.properties，全部可选）：</p>
 * <pre>
 *   systemmonitor.log.enabled = false   # 总开关，默认 false（不打印任何监控日志）
 *   systemmonitor.log.level   = warn    # 开启后，只打印 &gt;= 该级别的日志
 *                                       # 可选：debug / info / warn / error / off
 * </pre>
 *
 * <p>典型场景：</p>
 * <ul>
 *   <li>默认：完全静默，适合生产环境；</li>
 *   <li>排查采集失败：{@code enabled=true, level=error}，只看异常；</li>
 *   <li>全量调试：{@code enabled=true, level=debug}。</li>
 * </ul>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>懒加载 Holder：首次访问才读配置，避免类初始化顺序问题；</li>
 *   <li>读取配置包 try-catch：ConfigLoader 异常时回退为关闭，不影响监控本身；</li>
 *   <li>与 {@link Logger} 用法一致：{@code SystemMonitorLog.warn(log, "x {}", a)}
 *       对应 {@code log.warn("x {}", a)}，改造成本极低；</li>
 *   <li>仅作用于 systemmonitor 模块，其他模块日志不受影响。</li>
 * </ul>
 *
 * <p>后续扩展：新增系统监控组件时，请统一使用本类打日志，
 * 例如 {@code SystemMonitorLog.warn(log, "xxx 采集失败", e)}。</p>
 */
public final class SystemMonitorLog {

    private SystemMonitorLog() {}

    // ==================== 懒加载 ====================

    private static final class Holder {
        static final boolean ENABLED = readEnabled();
        static final int LEVEL = readLevel();
    }

    /** TRACE/DEBUG=1, INFO=2, WARN=3, ERROR=4, OFF=MAX */
    private static final int LV_DEBUG = 1;
    private static final int LV_INFO  = 2;
    private static final int LV_WARN  = 3;
    private static final int LV_ERROR = 4;
    private static final int LV_OFF   = Integer.MAX_VALUE;

    private static boolean readEnabled() {
        try {
            return "true".equalsIgnoreCase(
                    ConfigLoader.getProperty("systemmonitor.log.enabled", "false"));
        } catch (Throwable t) {
            // ConfigLoader 异常时回退为关闭，绝不拖垮监控
            return false;
        }
    }

    private static int readLevel() {
        try {
            String s = ConfigLoader.getProperty("systemmonitor.log.level", "warn");
            return parseLevel(s);
        } catch (Throwable t) {
            return LV_WARN;
        }
    }

    private static int parseLevel(String s) {
        if (s == null) return LV_WARN;
        switch (s.trim().toLowerCase()) {
            case "trace":
            case "debug": return LV_DEBUG;
            case "info":  return LV_INFO;
            case "warn":  return LV_WARN;
            case "error": return LV_ERROR;
            case "off":   return LV_OFF;
            default:      return LV_WARN;
        }
    }

    // ==================== 对外状态查询 ====================

    public static boolean isEnabled()      { return Holder.ENABLED; }
    public static boolean isDebugEnabled() { return Holder.ENABLED && Holder.LEVEL <= LV_DEBUG; }
    public static boolean isInfoEnabled()  { return Holder.ENABLED && Holder.LEVEL <= LV_INFO;  }
    public static boolean isWarnEnabled()  { return Holder.ENABLED && Holder.LEVEL <= LV_WARN;  }
    public static boolean isErrorEnabled() { return Holder.ENABLED && Holder.LEVEL <= LV_ERROR; }

    // ==================== 便捷日志方法 ====================

    public static void debug(Logger log, String msg, Object... args) {
        if (isDebugEnabled()) log.debug(msg, args);
    }

    public static void info(Logger log, String msg, Object... args) {
        if (isInfoEnabled()) log.info(msg, args);
    }

    public static void warn(Logger log, String msg, Object... args) {
        if (isWarnEnabled()) log.warn(msg, args);
    }

    public static void warn(Logger log, String msg, Throwable t) {
        if (isWarnEnabled()) log.warn(msg, t);
    }

    public static void error(Logger log, String msg, Object... args) {
        if (isErrorEnabled()) log.error(msg, args);
    }

    public static void error(Logger log, String msg, Throwable t) {
        if (isErrorEnabled()) log.error(msg, t);
    }
}