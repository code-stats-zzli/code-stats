package com.omni.common.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 驱动加载失败的结构化异常。
 *
 * <p>由 {@link DriverLoader} / {@link DriverResourceScanner} 抛出，
 * 携带完整诊断信息，便于上层（Controller / 前端）展示到界面：</p>
 * <ul>
 *   <li>{@code stage}：失败阶段（resolve_class / load_class / linkage / cast / instantiate / read_jar / not_found）</li>
 *   <li>{@code jarSource}：JAR 来源（driver_jar(表) / resources/drivers(目录) / classpath / all）</li>
 *   <li>{@code jarPath}：JAR 位置（id=xxx, file=xxx.jar 或绝对路径）</li>
 *   <li>{@code expectedClass}：期望的驱动类名（可能为 null）</li>
 *   <li>{@code candidates}：JAR 内实际存在的可能驱动类，供用户改 driverClass</li>
 *   <li>{@code hint}：多行修复建议</li>
 * </ul>
 *
 * <p>{@link #toDetails()} 直接序列化为 JSON 返回给前端；前端根据
 * {@code errorType === 'driver_load'} 渲染诊断信息。</p>
 */
public class DriverLoadException extends RuntimeException {

    private final String stage;
    private final String dbType;
    private final String version;
    private final String jarSource;
    private final String jarPath;
    private final String expectedClass;
    private final List<String> candidates;
    private final String hint;

    public DriverLoadException(String stage,
                               String dbType,
                               String version,
                               String jarSource,
                               String jarPath,
                               String expectedClass,
                               List<String> candidates,
                               String hint,
                               Throwable cause) {
        super(buildMessage(stage, dbType, expectedClass, hint), cause);
        this.stage = stage;
        this.dbType = dbType;
        this.version = version;
        this.jarSource = jarSource;
        this.jarPath = jarPath;
        this.expectedClass = expectedClass;
        this.candidates = candidates != null ? candidates : Collections.emptyList();
        this.hint = hint;
    }

    private static String buildMessage(String stage, String dbType, String expectedClass, String hint) {
        if ("not_found".equals(stage)) {
            String type = (dbType == null || dbType.isEmpty()) ? "未知类型" : dbType;
            return "未找到 " + type + " 数据库驱动：driver_jar 表、resources/drivers 目录、classpath 均无可用驱动";
        }
        if (expectedClass != null && !expectedClass.isEmpty()) {
            return "驱动加载失败 [" + stage + "]：类 " + expectedClass + " 不存在或不可用";
        }
        String firstLine = firstLine(hint);
        return "驱动加载失败 [" + stage + "]：" + (firstLine.isEmpty() ? "未知原因" : firstLine);
    }

    /**
     * 简短描述，用于 JSON 的 error 字段。
     *
     * <p>针对不同阶段给出可操作的文案：</p>
     * <ul>
     *   <li>{@code not_found}：明确"未找到 XX 驱动"并提示上传/检查</li>
     *   <li>{@code resolve_class}：提示无法推断类名，并告知候选类数量</li>
     *   <li>{@code load_class}：明确 JAR 中不存在指定类</li>
     * </ul>
     */
    public String getShortMessage() {
        if ("not_found".equals(stage)) {
            String type = (dbType == null || dbType.isEmpty()) ? "该数据库" : dbType;
            return "未找到 " + type + " 驱动，请先上传驱动 JAR 或检查驱动配置";
        }
        if ("resolve_class".equals(stage)) {
            return "无法从 JAR 推断驱动类名"
                    + (candidates.isEmpty() ? "" : "（发现 " + candidates.size() + " 个候选类）");
        }
        if ("load_class".equals(stage) && expectedClass != null && !expectedClass.isEmpty()) {
            return "JAR 中不存在类 [" + expectedClass + "]";
        }
        if (expectedClass != null && !expectedClass.isEmpty()) {
            return "驱动类 [" + expectedClass + "] 加载失败（阶段：" + stage + "）";
        }
        return "无法加载驱动（阶段：" + stage + "）";
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int idx = s.indexOf('\n');
        return (idx >= 0 ? s.substring(0, idx) : s).trim();
    }

    /** 结构化诊断，用于 JSON 的 details 字段 */
    public Map<String, Object> toDetails() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stage", stage);
        m.put("dbType", dbType);
        m.put("version", version);
        m.put("jarSource", jarSource);
        m.put("jarPath", jarPath);
        m.put("expectedClass", expectedClass);
        m.put("candidates", candidates);
        m.put("hint", hint);
        return m;
    }

    // ==================== Getters ====================

    public String getStage() { return stage; }
    public String getDbType() { return dbType; }
    public String getVersion() { return version; }
    public String getJarSource() { return jarSource; }
    public String getJarPath() { return jarPath; }
    public String getExpectedClass() { return expectedClass; }
    public List<String> getCandidates() { return candidates; }
    public String getHint() { return hint; }
}