package com.omni.framework.tomcat.util;

import com.omni.framework.spring.config.ConfigLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 访问日志过滤工具。
 *
 * <p>目的：前端存在若干高频轮询接口（系统日志 / 系统监控 / 3D 实时监控等），
 * 每次轮询都会在 {@code Connector} 产生 2 条 DEBUG、在
 * {@code DispatcherServlet} 产生 3 条 INFO，导致日志被刷屏。
 * 本类用于「按 URI 前缀」过滤这些访问日志，<b>只影响成功路径的访问日志</b>，
 * 错误日志（4xx/5xx / 异常堆栈）不受影响。</p>
 *
 * <p>配置项（application.properties，全部可选）：</p>
 * <pre>
 *   # 访问日志总开关：false 时关闭所有访问日志（默认 true）
 *   access.log.enabled=true
 *
 *   # 排除的 URI 前缀（逗号分隔）
 *   # 命中则不打印「连接建立 / 连接处理完成 / 收到请求 / 匹配处理器 / 请求处理完成」
 *   access.log.exclude-paths=/sys/api/log/read,/systemmonitor/api/,/3d/api/monitor-data,/3d/api/jstats/monitor,/3d/api/trace
 * </pre>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>懒加载：首次访问才读配置，避免类初始化顺序问题；</li>
 *   <li>读取配置包 try-catch：ConfigLoader 异常时回退为「默认关闭」，绝不影响请求处理；</li>
 *   <li>前缀匹配：命中即排除，不做正则，避免性能开销；</li>
 *   <li>无状态、线程安全：静态不可变列表 + volatile 惰性初始化。</li>
 * </ul>
 *
 * <p>后续扩展：新增高频接口时，只需在 access.log.exclude-paths 追加前缀即可，无需改代码。</p>
 */
public final class AccessLogFilter {

    private AccessLogFilter() {}

    /** 访问日志总开关；false 时所有访问日志都不打印 */
    private static final boolean ENABLED;
    /** 排除的 URI 前缀（不可变） */
    private static final List<String> EXCLUDE_PREFIXES;

    static {
        boolean enabled;
        try {
            enabled = !"false".equalsIgnoreCase(
                    ConfigLoader.getProperty("access.log.enabled", "true"));
        } catch (Throwable t) {
            enabled = true;
        }
        ENABLED = enabled;

        // 默认排除已知高频轮询接口，开箱即用
        final String DEFAULT_EXCLUDE =
                "/sys/api/log/read," +
                "/systemmonitor/api/," +
                "/3d/api/monitor-data," +
                "/3d/api/jstats/monitor," +
                "/3d/api/trace";

        String cfg;
        try {
            cfg = ConfigLoader.getProperty("access.log.exclude-paths", DEFAULT_EXCLUDE);
        } catch (Throwable t) {
            cfg = DEFAULT_EXCLUDE;
        }

        List<String> list = new ArrayList<>();
        if (cfg != null) {
            for (String s : cfg.split(",")) {
                String p = s.trim();
                if (!p.isEmpty() && !list.contains(p)) list.add(p);
            }
        }
        EXCLUDE_PREFIXES = java.util.Collections.unmodifiableList(list);
    }

    /** 访问日志是否启用 */
    public static boolean enabled() {
        return ENABLED;
    }

    /** 指定 URI 是否应打印访问日志（错误日志不经过此方法） */
    public static boolean shouldLog(String uri) {
        if (!ENABLED) return false;
        if (uri == null) return true;
        for (String prefix : EXCLUDE_PREFIXES) {
            if (uri.startsWith(prefix)) return false;
        }
        return true;
    }
}