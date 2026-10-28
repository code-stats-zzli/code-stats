package com.omni.business.assistance.systemcheck.config;

import java.io.InputStream;
import java.util.*;

/** 系统检查配置（读取自 system-check.properties，支持模块级详细配置） */
public class CheckConfig {

    public static final List<String> CHECK_IDS = Arrays.asList(
            "system-info", "firewall", "port", "rdp", "lock-screen",
            "credentials", "archive", "plaintext-scan",
            "dependency-vuln", "endpoint-security");

    public static final Map<String, String> CHECK_NAMES = new LinkedHashMap<>();
    public static final Map<String, String> CHECK_ICONS = new LinkedHashMap<>();
    public static final Map<String, Integer> DEFAULT_EST_SECONDS = new LinkedHashMap<>();

    static {
        CHECK_NAMES.put("system-info", "系统信息");
        CHECK_NAMES.put("firewall", "防火墙");
        CHECK_NAMES.put("port", "高危端口");
        CHECK_NAMES.put("rdp", "远程桌面");
        CHECK_NAMES.put("lock-screen", "屏保/锁屏");
        CHECK_NAMES.put("credentials", "凭据/软件");
        CHECK_NAMES.put("archive", "压缩包/安装包");
        CHECK_NAMES.put("plaintext-scan", "明文密码扫描");
        CHECK_NAMES.put("dependency-vuln", "依赖漏洞扫描");
        CHECK_NAMES.put("endpoint-security", "跨网/无线/U盘");

        CHECK_ICONS.put("system-info", "💻");
        CHECK_ICONS.put("firewall", "🔥");
        CHECK_ICONS.put("port", "🚪");
        CHECK_ICONS.put("rdp", "🖥️");
        CHECK_ICONS.put("lock-screen", "🔒");
        CHECK_ICONS.put("credentials", "🔑");
        CHECK_ICONS.put("archive", "📦");
        CHECK_ICONS.put("plaintext-scan", "📝");
        CHECK_ICONS.put("dependency-vuln", "⚠️");
        CHECK_ICONS.put("endpoint-security", "🌐");

        DEFAULT_EST_SECONDS.put("system-info", 1);
        DEFAULT_EST_SECONDS.put("firewall", 3);
        DEFAULT_EST_SECONDS.put("port", 5);
        DEFAULT_EST_SECONDS.put("rdp", 2);
        DEFAULT_EST_SECONDS.put("lock-screen", 2);
        DEFAULT_EST_SECONDS.put("credentials", 5);
        DEFAULT_EST_SECONDS.put("archive", 10);
        DEFAULT_EST_SECONDS.put("plaintext-scan", 30);
        DEFAULT_EST_SECONDS.put("dependency-vuln", 20);
        DEFAULT_EST_SECONDS.put("endpoint-security", 5);
    }

    // ==================== 默认值常量 ====================
    private static final List<String> DEFAULT_RISK_PORTS = Collections.unmodifiableList(Arrays.asList(
            "21","22","23","25","53","80","110","135","137","138","139","143","443",
            "445","1433","1521","3306","3389","5432","5900","6379","9042","9092",
            "9200","9300","11211","27017","2375","8080","8443","8888","5000","5001"));
    private static final List<String> DEFAULT_RISK_REQ_PORTS = Collections.unmodifiableList(Arrays.asList(
            "21","23","135","137","138","139","445","3306","3389","6379"));
    private static final long DEFAULT_PLAINTEXT_MAX_FILE = 15L * 1024 * 1024;
    private static final long DEFAULT_PLAINTEXT_MAX_DOC = 3L * 1024 * 1024;
    private static final int DEFAULT_DEP_DEPTH = 8;

    // ==================== 实例字段 ====================
    private int threadCount = 4;
    private final Map<String, Boolean> checkEnabled = new LinkedHashMap<>();
    /** moduleId → { 配置 key → 值 }（可为 List / Number / String） */
    private final Map<String, Map<String, Object>> moduleConfig = new LinkedHashMap<>();

    public CheckConfig() {
        for (String id : CHECK_IDS) checkEnabled.put(id, true);
        // 初始化默认模块配置（保证每个模块都有 default 值可读）
        setModuleValue("port", "riskPorts", new ArrayList<>(DEFAULT_RISK_PORTS));
        setModuleValue("port", "riskReqPorts", new ArrayList<>(DEFAULT_RISK_REQ_PORTS));
        setModuleValue("plaintext-scan", "maxFileSize", DEFAULT_PLAINTEXT_MAX_FILE);
        setModuleValue("plaintext-scan", "maxDocSize", DEFAULT_PLAINTEXT_MAX_DOC);
        setModuleValue("dependency-vuln", "maxDepth", DEFAULT_DEP_DEPTH);
    }

    // ==================== 模块配置读写 ====================

    public Map<String, Object> getModuleConfig(String moduleId) {
        return moduleConfig.computeIfAbsent(moduleId, k -> new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public <T> T getModuleValue(String moduleId, String key, T defaultValue) {
        Map<String, Object> m = getModuleConfig(moduleId);
        Object v = m.get(key);
        return v == null ? defaultValue : (T) v;
    }

    public void setModuleValue(String moduleId, String key, Object value) {
        getModuleConfig(moduleId).put(key, value);
    }

    /** 合并前端传入的模块配置（自动类型转换） */
    @SuppressWarnings("unchecked")
    public void mergeModuleConfig(Map<String, Map<String, Object>> overrides) {
        if (overrides == null) return;
        for (Map.Entry<String, Map<String, Object>> e : overrides.entrySet()) {
            String moduleId = e.getKey();
            Map<String, Object> incoming = e.getValue();
            if (incoming == null) continue;
            Map<String, Object> target = getModuleConfig(moduleId);
            for (Map.Entry<String, Object> f : incoming.entrySet()) {
                Object v = f.getValue();
                // 前端 list 类型可能传成 List 或逗号字符串，统一转 List<String>
                if (v instanceof String && (f.getKey().equals("riskPorts")
                        || f.getKey().equals("riskReqPorts"))) {
                    List<String> list = new ArrayList<>();
                    for (String s : ((String) v).split(",")) if (!s.trim().isEmpty()) list.add(s.trim());
                    v = list;
                }
                target.put(f.getKey(), v);
            }
        }
    }

    // ==================== 便捷读方法（兼容旧调用方） ====================

    @SuppressWarnings("unchecked")
    public List<String> getRiskPorts() {
        Object v = getModuleValue("port", "riskPorts", null);
        if (v instanceof List) return (List<String>) v;
        return DEFAULT_RISK_PORTS;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRiskReqPorts() {
        Object v = getModuleValue("port", "riskReqPorts", null);
        if (v instanceof List) return (List<String>) v;
        return DEFAULT_RISK_REQ_PORTS;
    }

    public long getPlaintextScanMaxFileSize() {
        Object v = getModuleValue("plaintext-scan", "maxFileSize", null);
        return v instanceof Number ? ((Number) v).longValue() : DEFAULT_PLAINTEXT_MAX_FILE;
    }

    public long getPlaintextScanMaxDocSize() {
        Object v = getModuleValue("plaintext-scan", "maxDocSize", null);
        return v instanceof Number ? ((Number) v).longValue() : DEFAULT_PLAINTEXT_MAX_DOC;
    }

    public int getDependencyVulnMaxDepth() {
        Object v = getModuleValue("dependency-vuln", "maxDepth", null);
        return v instanceof Number ? ((Number) v).intValue() : DEFAULT_DEP_DEPTH;
    }

    public int getThreadCount() { return threadCount; }
    public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
    public Map<String, Boolean> getCheckEnabled() { return checkEnabled; }

    // ==================== 加载 ====================

    public static CheckConfig load() {
        CheckConfig cfg = new CheckConfig();
        try (InputStream is = CheckConfig.class.getClassLoader()
                .getResourceAsStream("system-check.properties")) {
            if (is == null) return cfg;
            Properties p = new Properties();
            p.load(is);
            String tc = p.getProperty("system-check.thread-count");
            if (tc != null) {
                try { cfg.threadCount = Integer.parseInt(tc.trim()); } catch (Exception ignored) {}
            }
            // 端口列表
            List<String> rp = split(p.getProperty("system-check.risk-ports"));
            if (rp != null) cfg.setModuleValue("port", "riskPorts", rp);
            List<String> rrp = split(p.getProperty("system-check.risk-req-ports"));
            if (rrp != null) cfg.setModuleValue("port", "riskReqPorts", rrp);
            // 大小阈值
            String v = p.getProperty("system-check.plaintext-scan.max-file-size");
            if (v != null) cfg.setModuleValue("plaintext-scan", "maxFileSize", parseLong(v, DEFAULT_PLAINTEXT_MAX_FILE));
            v = p.getProperty("system-check.plaintext-scan.max-doc-size");
            if (v != null) cfg.setModuleValue("plaintext-scan", "maxDocSize", parseLong(v, DEFAULT_PLAINTEXT_MAX_DOC));
            v = p.getProperty("system-check.dependency-vuln.max-depth");
            if (v != null) cfg.setModuleValue("dependency-vuln", "maxDepth", parseInt(v, DEFAULT_DEP_DEPTH));
            // 模块开关
            for (String id : CHECK_IDS) {
                String s = p.getProperty("system-check.check." + id + ".enabled");
                if (s != null) cfg.checkEnabled.put(id, Boolean.parseBoolean(s.trim()));
            }
        } catch (Exception ignored) {}
        return cfg;
    }

    private static List<String> split(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        List<String> out = new ArrayList<>();
        for (String t : s.split(",")) if (!t.trim().isEmpty()) out.add(t.trim());
        return out.isEmpty() ? null : out;
    }
    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // ==================== 输出元数据（含详细配置字段） ====================

    public Map<String, Object> toMetaMap(java.util.List<com.omni.business.assistance.systemcheck.command.SystemCheckCommand> commands) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("threadCount", threadCount);

        java.util.List<Map<String, Object>> checks = new ArrayList<>();
        for (com.omni.business.assistance.systemcheck.command.SystemCheckCommand cmd : commands) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", cmd.getId());
            c.put("name", cmd.getName());
            c.put("icon", CHECK_ICONS.getOrDefault(cmd.getId(), "📦"));
            c.put("enabled", checkEnabled.getOrDefault(cmd.getId(), true));
            c.put("estimatedSeconds", cmd.getEstimatedSeconds(this));
            c.put("configFields", cmd.getConfigFields(this));
            checks.add(c);
        }
        root.put("checks", checks);
        return root;
    }
}