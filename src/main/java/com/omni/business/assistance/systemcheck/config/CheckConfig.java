package com.omni.business.assistance.systemcheck.config;

import com.omni.business.assistance.systemcheck.command.SystemCheckCommand;

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
    }

    // 实例字段
    private int threadCount = 4;
    private final Map<String, Boolean> checkEnabled = new LinkedHashMap<>();
    /** moduleId → { 配置 key → 值 }（值类型：List<String>/Number/String/Boolean） */
    private final Map<String, Map<String, Object>> moduleConfig = new LinkedHashMap<>();

    public CheckConfig() {
        for (String id : CHECK_IDS) checkEnabled.put(id, true);
    }

    // ==================== 模块配置读写（类型安全 getter） ====================

    public Map<String, Object> getModuleConfig(String moduleId) {
        return moduleConfig.computeIfAbsent(moduleId, k -> new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public List<String> getModuleList(String moduleId, String key, List<String> def) {
        Object v = getModuleConfig(moduleId).get(key);
        if (v instanceof List) return (List<String>) v;
        if (v instanceof String) {
            List<String> out = new ArrayList<>();
            for (String s : ((String) v).split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
            return out;
        }
        return def;
    }

    public String getModuleString(String moduleId, String key, String def) {
        Object v = getModuleConfig(moduleId).get(key);
        return v == null ? def : String.valueOf(v);
    }

    public int getModuleInt(String moduleId, String key, int def) {
        Object v = getModuleConfig(moduleId).get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (Exception ignored) {}
        }
        return def;
    }

    public long getModuleLong(String moduleId, String key, long def) {
        Object v = getModuleConfig(moduleId).get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong(((String) v).trim()); } catch (Exception ignored) {}
        }
        return def;
    }

    public boolean getModuleBool(String moduleId, String key, boolean def) {
        Object v = getModuleConfig(moduleId).get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return Boolean.parseBoolean(((String) v).trim());
        return def;
    }

    public void setModuleValue(String moduleId, String key, Object value) {
        getModuleConfig(moduleId).put(key, value);
    }

    /** 合并前端传入的模块配置 */
    @SuppressWarnings("unchecked")
    public void mergeModuleConfig(Map<String, Map<String, Object>> overrides) {
        if (overrides == null) return;
        for (Map.Entry<String, Map<String, Object>> e : overrides.entrySet()) {
            Map<String, Object> incoming = e.getValue();
            if (incoming == null) continue;
            Map<String, Object> target = getModuleConfig(e.getKey());
            for (Map.Entry<String, Object> f : incoming.entrySet()) {
                Object v = f.getValue();
                // 前端 list 类型可能传成 List 或逗号字符串
                if (v instanceof String) {
                    String s = (String) v;
                    if (s.contains(",") && !f.getKey().matches("(?i).*pattern.*|.*regex.*")) {
                        List<String> list = new ArrayList<>();
                        for (String t : s.split(",")) if (!t.trim().isEmpty()) list.add(t.trim());
                        v = list;
                    }
                }
                target.put(f.getKey(), v);
            }
        }
    }

    // ==================== 兼容旧调用方 ====================

    public List<String> getRiskPorts() {
        return getModuleList("port", "riskPorts", defaultRiskPorts());
    }
    public List<String> getRiskReqPorts() {
        return getModuleList("port", "riskReqPorts", defaultRiskReqPorts());
    }
    public long getPlaintextScanMaxFileSize() {
        return getModuleLong("plaintext-scan", "maxFileSize", 15L * 1024 * 1024);
    }
    public long getPlaintextScanMaxDocSize() {
        return getModuleLong("plaintext-scan", "maxDocSize", 3L * 1024 * 1024);
    }
    public int getDependencyVulnMaxDepth() {
        return getModuleInt("dependency-vuln", "maxDepth", 8);
    }

    private static List<String> defaultRiskPorts() {
        return Arrays.asList("21","22","23","25","53","80","110","135","137","138","139",
                "143","443","445","1433","1521","3306","3389","5432","5900","6379",
                "9042","9092","9200","9300","11211","27017","2375","8080","8443","8888","5000","5001");
    }
    private static List<String> defaultRiskReqPorts() {
        return Arrays.asList("21","23","135","137","138","139","445","3306","3389","6379");
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

            // 端口模块
            loadList(p, cfg, "port", "system-check.port.riskPorts", "riskPorts");
            loadList(p, cfg, "port", "system-check.port.riskReqPorts", "riskReqPorts");

            // 明文扫描模块
            loadLong(p, cfg, "plaintext-scan", "system-check.plaintext-scan.maxFileSize", "maxFileSize");
            loadLong(p, cfg, "plaintext-scan", "system-check.plaintext-scan.maxDocSize", "maxDocSize");
            loadList(p, cfg, "plaintext-scan", "system-check.plaintext-scan.passwordKeywords", "passwordKeywords");
            loadList(p, cfg, "plaintext-scan", "system-check.plaintext-scan.textExtensions", "textExtensions");
            loadList(p, cfg, "plaintext-scan", "system-check.plaintext-scan.docExtensions", "docExtensions");
            loadList(p, cfg, "plaintext-scan", "system-check.plaintext-scan.skipDirs", "skipDirs");
            loadList(p, cfg, "plaintext-scan", "system-check.plaintext-scan.skipKeywords", "skipKeywords");
            loadList(p, cfg, "plaintext-scan", "system-check.plaintext-scan.placeholderBrackets", "placeholderBrackets");
            loadStr(p, cfg, "plaintext-scan", "system-check.plaintext-scan.assignRegex", "assignRegex");

            // 压缩包模块
            loadLong(p, cfg, "archive", "system-check.archive.minSizeMB", "minSizeMB");
            loadList(p, cfg, "archive", "system-check.archive.scanDirs", "scanDirs");
            loadList(p, cfg, "archive", "system-check.archive.archiveExtensions", "archiveExtensions");
            loadStr(p, cfg, "archive", "system-check.archive.openSourcePattern", "openSourcePattern");

            // 凭据模块
            loadList(p, cfg, "credentials", "system-check.credentials.credentialFiles", "credentialFiles");
            loadList(p, cfg, "credentials", "system-check.credentials.browserDirsWin", "browserDirsWin");
            loadList(p, cfg, "credentials", "system-check.credentials.browserDirsLinux", "browserDirsLinux");
            loadStr(p, cfg, "credentials", "system-check.credentials.commercialPattern", "commercialPattern");

            // 跨网模块
            loadStr(p, cfg, "endpoint-security", "system-check.endpoint-security.crossNetPattern", "crossNetPattern");
            loadStr(p, cfg, "endpoint-security", "system-check.endpoint-security.wifiPattern", "wifiPattern");

            // 依赖漏洞模块
            loadList(p, cfg, "dependency-vuln", "system-check.dependency-vuln.manifestFiles", "manifestFiles");
            loadList(p, cfg, "dependency-vuln", "system-check.dependency-vuln.skipDirs", "skipDirs");
            loadStr(p, cfg, "dependency-vuln", "system-check.dependency-vuln.customVulnDb", "customVulnDb");

            // 模块开关
            for (String id : CHECK_IDS) {
                String s = p.getProperty("system-check.check." + id + ".enabled");
                if (s != null) cfg.checkEnabled.put(id, Boolean.parseBoolean(s.trim()));
            }
        } catch (Exception ignored) {}
        return cfg;
    }

    private static void loadList(Properties p, CheckConfig cfg, String module, String propKey, String cfgKey) {
        String s = p.getProperty(propKey);
        if (s == null) return;
        List<String> list = new ArrayList<>();
        for (String t : s.split(",")) if (!t.trim().isEmpty()) list.add(t.trim());
        if (!list.isEmpty()) cfg.setModuleValue(module, cfgKey, list);
    }
    private static void loadLong(Properties p, CheckConfig cfg, String module, String propKey, String cfgKey) {
        String s = p.getProperty(propKey);
        if (s == null) return;
        try { cfg.setModuleValue(module, cfgKey, Long.parseLong(s.trim())); } catch (Exception ignored) {}
    }
    private static void loadStr(Properties p, CheckConfig cfg, String module, String propKey, String cfgKey) {
        String s = p.getProperty(propKey);
        if (s != null) cfg.setModuleValue(module, cfgKey, s.trim());
    }

    // ==================== 输出元数据（含详细配置字段） ====================

    public Map<String, Object> toMetaMap(List<SystemCheckCommand> commands) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("threadCount", threadCount);

        List<Map<String, Object>> checks = new ArrayList<>();
        for (SystemCheckCommand cmd : commands) {
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