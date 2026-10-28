package com.omni.business.assistance.systemcheck.config;

import java.io.InputStream;
import java.util.*;

/** 系统检查配置（读取自 system-check.properties） */
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

    private int threadCount = 4;
    private List<String> riskPorts = new ArrayList<>();
    private List<String> riskReqPorts = new ArrayList<>();
    private final Map<String, Boolean> checkEnabled = new LinkedHashMap<>();
    private long plaintextScanMaxFileSize = 15L * 1024 * 1024;
    private long plaintextScanMaxDocSize = 3L * 1024 * 1024;
    private int dependencyVulnMaxDepth = 8;

    public CheckConfig() {
        for (String id : CHECK_IDS) checkEnabled.put(id, true);
        riskPorts = Arrays.asList("21","22","23","25","53","80","110","135","137",
                "138","139","143","443","445","1433","1521","3306","3389",
                "5432","5900","6379","9042","9092","9200","9300","11211",
                "27017","2375","8080","8443","8888","5000","5001");
        riskReqPorts = Arrays.asList("21","23","135","137","138","139","445",
                "3306","3389","6379");
    }

    public static CheckConfig load() {
        CheckConfig cfg = new CheckConfig();
        try (InputStream is = CheckConfig.class.getClassLoader()
                .getResourceAsStream("system-check.properties")) {
            if (is == null) return cfg;
            Properties p = new Properties();
            p.load(is);
            cfg.threadCount = Integer.parseInt(p.getProperty("system-check.thread-count", "4"));
            cfg.riskPorts = split(p.getProperty("system-check.risk-ports", ""), cfg.riskPorts);
            cfg.riskReqPorts = split(p.getProperty("system-check.risk-req-ports", ""), cfg.riskReqPorts);
            for (String id : CHECK_IDS) {
                String v = p.getProperty("system-check.check." + id + ".enabled");
                if (v != null) cfg.checkEnabled.put(id, Boolean.parseBoolean(v.trim()));
            }
            String v = p.getProperty("system-check.plaintext-scan.max-file-size");
            if (v != null) cfg.plaintextScanMaxFileSize = Long.parseLong(v);
            v = p.getProperty("system-check.plaintext-scan.max-doc-size");
            if (v != null) cfg.plaintextScanMaxDocSize = Long.parseLong(v);
            v = p.getProperty("system-check.dependency-vuln.max-depth");
            if (v != null) cfg.dependencyVulnMaxDepth = Integer.parseInt(v);
        } catch (Exception ignored) { }
        return cfg;
    }

    private static List<String> split(String s, List<String> def) {
        if (s == null || s.trim().isEmpty()) return def;
        List<String> out = new ArrayList<>();
        for (String t : s.split(",")) if (!t.trim().isEmpty()) out.add(t.trim());
        return out.isEmpty() ? def : out;
    }

    public int getThreadCount() { return threadCount; }
    public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
    public List<String> getRiskPorts() { return riskPorts; }
    public List<String> getRiskReqPorts() { return riskReqPorts; }
    public Map<String, Boolean> getCheckEnabled() { return checkEnabled; }
    public long getPlaintextScanMaxFileSize() { return plaintextScanMaxFileSize; }
    public long getPlaintextScanMaxDocSize() { return plaintextScanMaxDocSize; }
    public int getDependencyVulnMaxDepth() { return dependencyVulnMaxDepth; }

    public Map<String, Object> toMetaMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("threadCount", threadCount);
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : CHECK_IDS) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", id);
            c.put("name", CHECK_NAMES.get(id));
            c.put("icon", CHECK_ICONS.get(id));
            c.put("enabled", checkEnabled.getOrDefault(id, true));
            list.add(c);
        }
        m.put("checks", list);
        m.put("riskPorts", riskPorts);
        m.put("riskReqPorts", riskReqPorts);
        return m;
    }
}