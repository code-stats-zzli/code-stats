package com.omni.business.assistance.sys.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 系统参数服务。
 *
 * <p>存储：{@code user.dir/resources/others/system-params.json}（JSON，实时读写，最高优先级）。</p>
 * <p>读取优先级：JSON 文件 &gt; classpath application.properties。</p>
 *
 * <p>导出格式（对齐「代码分析 → 代码导入」）：</p>
 * <ul>
 *   <li>{@code omni-pack}：完整项目包，推荐；可直接被「代码导入」还原为
 *       {@code resources/others/system-params.json}</li>
 *   <li>{@code file-block}：file 围栏 + path 块</li>
 *   <li>{@code merge-file}：// ======== path ======== 分隔头</li>
 *   <li>{@code plain-json}：纯 JSON（兼容旧方式，仅供人工编辑）</li>
 * </ul>
 *
 * <p>导入：自动识别上述 4 种格式，只应用 MODIFIABLE 中存在的 Key。</p>
 */
@Service
public class SystemParamService {
    private static final Logger log = LoggerFactory.getLogger(SystemParamService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 参数文件名（不含路径） */
    private static final String PARAMS_FILE_NAME = "system-params.json";
    /** 参数文件相对项目根目录的路径（omni-pack 导出使用） */
    private static final String PARAMS_REL_PATH = "resources/others/" + PARAMS_FILE_NAME;

    /**
     * 三反引号常量。目的：避免本源码中出现连续三个反引号，
     * 导致自身被 file-block / omni-pack 打包解析时截断。
     */
    private static final String FENCE = "`" + "`" + "`";

    // ==================== 格式解析正则（与代码导入保持一致） ====================
    private static final Pattern MERGE_HEADER = Pattern.compile(
            "^// ======== (.+?) ========\\s*$", Pattern.MULTILINE);
    private static final Pattern FILE_BLOCK = Pattern.compile(
            FENCE + "file:([^\\r\\n]+?)\\s*\\r?\\n([\\s\\S]*?)\\r?\\n" + FENCE, Pattern.MULTILINE);
    private static final Pattern OMNI_PACK = Pattern.compile(
            FENCE + "omni-pack\\s*\\r?\\n([\\s\\S]*?)\\r?\\n" + FENCE, Pattern.MULTILINE);

    private static final List<ParamMeta> MODIFIABLE = new ArrayList<>();
    private static final List<ParamMeta> IMMUTABLE = new ArrayList<>();

    static {
        // ==================== 可修改参数 ====================
        // —— 服务 / 框架 ——
        add(MODIFIABLE, "server.port", "服务端 HTTP 监听端口",
                "Bootstrap.java, Catalina.java, SpringApplication.java, MonitorDataScheduler.java",
                "number", true);
        add(MODIFIABLE, "context.path", "应用上下文路径，例如 /omni",
                "Catalina.java, DispatcherServlet.java, webapps/demo/global.js", "text", true);
        add(MODIFIABLE, "servlet.class", "DispatcherServlet 全限定类名",
                "Catalina.java", "text", true);
        add(MODIFIABLE, "server.home.redirect.enabled", "目录访问是否自动重定向到主页面",
                "DispatcherServlet.java, SimplePipeline.java", "boolean", true);
        add(MODIFIABLE, "server.home.redirect.url", "重定向目标路径",
                "DispatcherServlet.java, SimplePipeline.java", "text", true);

        // —— 截图助手 / OCR ——
        add(MODIFIABLE, "screenshot.assistant.enabled", "是否启用截图助手",
                "ScreenshotRunner.java, ScreenshotAssistantBootstrap.java", "boolean", true);
        add(MODIFIABLE, "screenshot.save.dir", "截图保存目录（留空默认 user.home/screenshots）",
                "ScreenshotService.java", "text", true);
        add(MODIFIABLE, "ocr.tessdata.path", "Tesseract 语言包目录",
                "OcrController.java, OcrService.java", "text", true);

        // —— 网页爬虫 ——
        add(MODIFIABLE, "playwright.enabled", "是否启用 Playwright 无头浏览器",
                "PlaywrightStealthUtil.java, HttpUtil.java", "boolean", true);

        // —— AI Agent ——
        add(MODIFIABLE, "ai.backend.default", "默认 AI 后端：ollama / deepseek / custom / project",
                "AIChatService.java", "select", true,
                "ollama", "deepseek", "custom", "project");
        add(MODIFIABLE, "ollama.api.url", "Ollama Chat API 地址",
                "OllamaChatBackend.java", "text", true);
        add(MODIFIABLE, "custom.api.url", "自定义 OpenAI 兼容 API 地址",
                "CustomChatBackend.java", "text", true);
        add(MODIFIABLE, "custom.api.key", "自定义 API Key（敏感，留空不修改）",
                "CustomChatBackend.java", "password", true);
        add(MODIFIABLE, "deepseek.api.key", "DeepSeek API Key（敏感，留空不修改）",
                "DeepSeekChatBackend.java", "password", true);
        add(MODIFIABLE, "project.assistant.kb.path", "项目助手知识库文件（classpath，逗号分隔）",
                "ProjectAssistantService.java", "text", true);
        add(MODIFIABLE, "project.assistant.ngram.fallback", "项目助手是否启用 ngram 降级",
                "ProjectAssistantService.java, CharNgramLM.java", "boolean", true);

        // —— 日志 ——
        add(MODIFIABLE, "access.log.enabled", "访问日志总开关",
                "AccessLogFilter.java, Connector.java, DispatcherServlet.java", "boolean", true);
        add(MODIFIABLE, "access.log.exclude-paths", "访问日志排除的 URI 前缀（逗号分隔）",
                "AccessLogFilter.java", "text", true);
        add(MODIFIABLE, "systemmonitor.log.enabled", "系统监控模块日志开关",
                "SystemMonitorLog.java", "boolean", true);
        add(MODIFIABLE, "systemmonitor.log.level", "系统监控模块日志级别",
                "SystemMonitorLog.java", "select", true,
                "debug", "info", "warn", "error", "off");
        add(MODIFIABLE, "sys.log.dir", "系统日志目录（留空自动探测）",
                "SysLogService.java", "text", true);

        // —— 监控调度 ——
        add(MODIFIABLE, "monitor.scheduler.enabled", "监控数据定时采集开关",
                "MonitorDataScheduler.java", "boolean", true);
        add(MODIFIABLE, "monitor.scheduler.url", "监控数据采集 URL",
                "MonitorDataScheduler.java", "text", true);
        add(MODIFIABLE, "monitor.scheduler.period", "监控采集周期（秒）",
                "MonitorDataScheduler.java", "number", true);
        add(MODIFIABLE, "monitor.scheduler.output-dir", "监控数据输出目录",
                "MonitorDataScheduler.java", "text", true);

        // —— 代码分析 ——
        add(MODIFIABLE, "code.analysis.download.allowed-roots",
                "代码分析合并文件下载白名单根目录（逗号分隔）",
                "CodeAnalysisController.java", "text", false);
        add(MODIFIABLE, "method.scan.rootDir", "方法级调用链默认扫描目录",
                "MethodAnalysisService.java", "text", true);
        add(MODIFIABLE, "method.scan.defaultMaxDepth", "方法调用树默认最大深度",
                "MethodAnalysisService.java", "number", true);
        add(MODIFIABLE, "method.scan.excludeTest", "方法扫描是否排除 test 目录",
                "MethodAnalysisService.java", "boolean", true);
        add(MODIFIABLE, "method.scan.excludePaths", "方法扫描排除目录（逗号分隔）",
                "MethodAnalysisService.java", "text", true);
        add(MODIFIABLE, "method.scan.cacheTtlMs", "方法调用链缓存 TTL（毫秒）",
                "MethodAnalysisService.java", "number", true);

        // —— 代码导入 / 文件管理 ——
        add(MODIFIABLE, "files.local.root", "本地文件管理根目录",
                "DirectoryService.java, FilesController.java", "text", true);
        add(MODIFIABLE, "import.file.allowed-roots", "代码导入允许写入的根目录（逗号分隔）",
                "ImportFileService.java", "text", false);
        add(MODIFIABLE, "import.file.max-file-size", "代码导入单文件最大字节数",
                "ImportFileService.java", "number", false);
        add(MODIFIABLE, "import.file.max-files", "代码导入单次最大文件数",
                "ImportFileService.java", "number", false);

        // —— 解压 / 压缩 ——
        add(MODIFIABLE, "unzip.max-file-size", "解压最大单文件字节数",
                "UnzipService.java", "number", true);
        add(MODIFIABLE, "unzip.max-files", "解压最大文件数",
                "UnzipService.java", "number", true);
        add(MODIFIABLE, "unzip.max-total-bytes", "解压最大总字节数",
                "UnzipService.java", "number", true);
        add(MODIFIABLE, "compress.max-entry-size", "压缩最大单文件字节数",
                "CompressService.java", "number", true);
        add(MODIFIABLE, "compress.max-files", "压缩最大文件数",
                "CompressService.java", "number", true);
        add(MODIFIABLE, "compress.max-total-bytes", "压缩最大总字节数",
                "CompressService.java", "number", true);

        // —— 驱动 / SSH / 转换 ——
        add(MODIFIABLE, "driver.resource.dir", "驱动资源目录（留空使用 user.dir/resources/drivers）",
                "DriverResourceScanner.java", "text", true);
        add(MODIFIABLE, "ssh.store.path", "SSH 连接配置文件路径",
                "SshConnectionService.java", "text", true);
        add(MODIFIABLE, "converter.tmp-dir", "格式转换临时目录",
                "TempFileManager.java", "text", true);

        // ==================== 不可修改参数 ====================
        addImmutable(IMMUTABLE, "db.url", "主数据库 JDBC URL",
                "DataSourceUtils.java, AbatractDatabaseMetadataService.java", "text");
        addImmutable(IMMUTABLE, "db.username", "主数据库用户名",
                "DataSourceUtils.java", "text");
        addImmutable(IMMUTABLE, "db.password", "主数据库密码（敏感）",
                "DataSourceUtils.java", "password");
        addImmutable(IMMUTABLE, "db.maxActive", "主数据库连接池最大活跃数",
                "DataSourceUtils.java", "number");
        addImmutable(IMMUTABLE, "db.maxIdle", "主数据库连接池最大空闲数",
                "DataSourceUtils.java", "number");
        addImmutable(IMMUTABLE, "db.minIdle", "主数据库连接池最小空闲数",
                "DataSourceUtils.java", "number");
        addImmutable(IMMUTABLE, "db.maxWaitMillis", "主数据库连接池获取超时（毫秒）",
                "DataSourceUtils.java", "number");
        addImmutable(IMMUTABLE, "db.validationQuery", "主数据库连接验证 SQL",
                "DataSourceUtils.java", "text");

        addImmutable(IMMUTABLE, "cluster.master.id", "集群主节点 ID",
                "ClusterService.java", "text");
        addImmutable(IMMUTABLE, "cluster.master.token", "集群 Token（敏感）",
                "ClusterService.java, WorkerBootstrap.java", "password");
        addImmutable(IMMUTABLE, "cluster.master.url", "集群主服务访问 URL（启动时确定）",
                "ClusterService.java", "text");

        addImmutableSys(IMMUTABLE, "user.dir", "JVM 工作目录",
                "（系统属性，所有基于 user.dir 的文件操作）");
        addImmutableSys(IMMUTABLE, "user.home", "用户主目录", "（系统属性）");
        addImmutableSys(IMMUTABLE, "java.home", "JVM 安装目录", "（系统属性）");
        addImmutableSys(IMMUTABLE, "java.version", "JVM 版本", "（系统属性）");
        addImmutableSys(IMMUTABLE, "os.name", "操作系统名称", "（系统属性）");
        addImmutableSys(IMMUTABLE, "os.arch", "操作系统架构", "（系统属性）");
    }

    // ==================== 元数据注册辅助 ====================

    private static void add(List<ParamMeta> list, String key, String desc, String file,
                            String type, boolean needRestart, String... options) {
        ParamMeta m = new ParamMeta();
        m.key = key;
        m.description = desc;
        m.file = file;
        m.type = type;
        m.needRestart = needRestart;
        m.editable = true;
        m.sensitive = isSensitive(key);
        m.options = options == null ? new ArrayList<>() : Arrays.asList(options);
        list.add(m);
    }

    private static void addImmutable(List<ParamMeta> list, String key, String desc,
                                     String file, String type) {
        ParamMeta m = new ParamMeta();
        m.key = key;
        m.description = desc;
        m.file = file;
        m.type = type;
        m.needRestart = true;
        m.editable = false;
        m.sensitive = isSensitive(key);
        m.options = new ArrayList<>();
        list.add(m);
    }

    private static void addImmutableSys(List<ParamMeta> list, String key, String desc, String file) {
        ParamMeta m = new ParamMeta();
        m.key = key;
        m.description = desc;
        m.file = file;
        m.type = "text";
        m.needRestart = true;
        m.editable = false;
        m.sensitive = false;
        m.fromSystemProperty = true;
        m.options = new ArrayList<>();
        list.add(m);
    }

    // ==================== 对外 API ====================

    public Map<String, Object> list() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("jsonFile", ConfigLoader.getJsonFilePath().toString());
        out.put("jsonFileExists", Files.isRegularFile(ConfigLoader.getJsonFilePath()));
        out.put("paramsRelPath", PARAMS_REL_PATH);
        out.put("modifiable", buildItems(MODIFIABLE, true));
        out.put("immutable", buildItems(IMMUTABLE, false));
        return out;
    }

    public synchronized Map<String, Object> update(String key, String value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (key == null || key.trim().isEmpty()) {
            return fail(out, "key 不能为空");
        }
        key = key.trim();
        ParamMeta meta = findMeta(MODIFIABLE, key);
        if (meta == null) {
            return fail(out, "该参数不存在或不允许修改: " + key);
        }
        if (value == null) value = "";
        if (value.contains("\r") || value.contains("\n")) {
            return fail(out, "值不能包含换行符");
        }
        if (value.length() > 4000) {
            return fail(out, "值长度不能超过 4000 字符");
        }
        if (meta.sensitive && value.trim().isEmpty()) {
            return fail(out, "敏感参数留空表示不修改；如需修改请输入新值");
        }

        try {
            ConfigLoader.setProperty(key, value);
            log.info("系统参数已保存到 JSON: {}={}", key, meta.sensitive ? "******" : value);
            out.put("success", true);
            out.put("key", key);
            out.put("value", meta.sensitive ? "" : value);
            out.put("needRestart", meta.needRestart);
            out.put("message", meta.needRestart
                    ? "已保存到 system-params.json，重启后生效"
                    : "已保存到 system-params.json，立即生效");
            return out;
        } catch (Exception e) {
            log.error("保存系统参数失败: " + key, e);
            return fail(out, "保存失败: " + e.getMessage());
        }
    }

    // ==================== 导出（4 种格式） ====================

    /**
     * 导出系统参数。
     *
     * @param format omni-pack（默认） / file-block / merge-file / plain-json
     * @return 对应格式的完整文本
     */
    public String exportAsFormat(String format) {
        Map<String, String> params = collectCurrentParams();
        String json;
        try {
            json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(params);
        } catch (Exception e) {
            log.error("序列化参数失败", e);
            return "{}";
        }

        String fmt = format == null ? "omni-pack" : format.trim().toLowerCase();

        if ("merge-file".equals(fmt)) {
            return "// ======== " + PARAMS_REL_PATH + " ========\n"
                    + json + "\n";
        }

        if ("file-block".equals(fmt)) {
            return FENCE + "file:" + PARAMS_REL_PATH + "\n"
                    + json + "\n" + FENCE + "\n";
        }

        if ("plain-json".equals(fmt)) {
            return json;
        }

        // 默认 omni-pack：完整项目包，可直接被「代码分析 → 代码导入」还原
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("name", "system-params");
        pack.put("version", "1.0");
        pack.put("description",
                "CodeStats 系统参数配置。"
                + "导入到「代码分析 → 代码导入」时："
                + "targetRoot 设为项目根目录（user.dir），"
                + "文件将完整还原到 resources/others/system-params.json。"
                + "敏感参数值已留空，如需完整备份请手动补充。");
        pack.put("targetRoot", "");
        pack.put("conflict", "OVERWRITE");
        String packJson;
        try {
            packJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(pack);
        } catch (Exception e) {
            packJson = "{}";
        }
        return FENCE + "omni-pack\n" + packJson + "\n" + FENCE + "\n\n"
                + FENCE + "file:" + PARAMS_REL_PATH + "\n"
                + json + "\n" + FENCE + "\n";
    }

    // ==================== 导入（支持 4 种格式 + 自动识别） ====================

    public synchronized Map<String, Object> importFromFormat(String content, String format) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (content == null || content.trim().isEmpty()) {
            return fail(out, "导入内容不能为空");
        }

        String jsonToImport;
        String detectedFormat;

        String fmt = format == null ? "auto" : format.trim().toLowerCase();

        if ("auto".equals(fmt) || "".equals(fmt)) {
            if (looksLikePlainJson(content)) {
                jsonToImport = content.trim();
                detectedFormat = "plain-json";
            } else if (looksLikeOmniPack(content)) {
                jsonToImport = findParamsFile(parseFileBlock(content));
                detectedFormat = "omni-pack";
            } else if (looksLikeFileBlock(content)) {
                jsonToImport = findParamsFile(parseFileBlock(content));
                detectedFormat = "file-block";
            } else if (looksLikeMergeFile(content)) {
                jsonToImport = findParamsFile(parseMergeFile(content));
                detectedFormat = "merge-file";
            } else {
                return fail(out, "无法识别格式。支持 omni-pack / file-block / merge-file / 纯 JSON");
            }
        } else if ("omni-pack".equals(fmt)) {
            jsonToImport = findParamsFile(parseFileBlock(content));
            detectedFormat = "omni-pack";
        } else if ("file-block".equals(fmt)) {
            jsonToImport = findParamsFile(parseFileBlock(content));
            detectedFormat = "file-block";
        } else if ("merge-file".equals(fmt)) {
            jsonToImport = findParamsFile(parseMergeFile(content));
            detectedFormat = "merge-file";
        } else if ("plain-json".equals(fmt) || "json".equals(fmt)) {
            jsonToImport = content.trim();
            detectedFormat = "plain-json";
        } else {
            return fail(out, "不支持的格式: " + format);
        }

        if (jsonToImport == null || jsonToImport.trim().isEmpty()) {
            return fail(out, "未在内容中找到 " + PARAMS_REL_PATH + " 文件块");
        }

        Map<String, Object> result = applyParamsJson(jsonToImport);
        result.put("detectedFormat", detectedFormat);
        return result;
    }

    // ==================== 格式解析 ====================

    private Map<String, String> parseMergeFile(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = MERGE_HEADER.matcher(content);
        List<int[]> ranges = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        while (m.find()) {
            ranges.add(new int[]{m.start(), m.end()});
            paths.add(m.group(1).trim());
        }
        for (int i = 0; i < ranges.size(); i++) {
            int start = ranges.get(i)[1];
            int end = (i + 1 < ranges.size()) ? ranges.get(i + 1)[0] : content.length();
            String blockContent = trimNewlines(content.substring(start, end));
            result.put(paths.get(i), blockContent);
        }
        return result;
    }

    private Map<String, String> parseFileBlock(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher m = FILE_BLOCK.matcher(content);
        while (m.find()) {
            result.put(m.group(1).trim(), m.group(2));
        }
        return result;
    }

    /**
     * 从解析后的文件列表中找到 system-params.json。
     * 兼容：system-params.json / resources/others/system-params.json / 任意前缀
     */
    private String findParamsFile(Map<String, String> files) {
        if (files == null || files.isEmpty()) return null;
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey().replace('\\', '/');
            if (path.endsWith(PARAMS_FILE_NAME)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String trimNewlines(String s) {
        int start = 0, end = s.length();
        while (start < end && (s.charAt(start) == '\n' || s.charAt(start) == '\r')) start++;
        while (end > start && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(start, end);
    }

    private boolean looksLikePlainJson(String s) {
        String t = s.trim();
        return t.startsWith("{") && t.endsWith("}")
                && !t.contains(FENCE) && !t.startsWith("//");
    }

    private boolean looksLikeOmniPack(String s) {
        return s.contains(FENCE + "omni-pack");
    }

    private boolean looksLikeFileBlock(String s) {
        return s.contains(FENCE + "file:");
    }

    private boolean looksLikeMergeFile(String s) {
        return MERGE_HEADER.matcher(s).find();
    }

    // ==================== 应用参数 ====================

    private Map<String, Object> applyParamsJson(String json) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> raw;
        try {
            raw = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return fail(out, "解析参数 JSON 失败: " + e.getMessage());
        }

        // 兼容 {"params": {...}} / {"modifiable": {...}} / 扁平对象
        Map<String, Object> params;
        if (raw.get("params") instanceof Map) {
            params = (Map<String, Object>) raw.get("params");
        } else if (raw.get("modifiable") instanceof Map) {
            params = (Map<String, Object>) raw.get("modifiable");
        } else {
            params = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                String k = e.getKey();
                if ("version".equals(k) || "exportedAt".equals(k)
                        || "jsonFile".equals(k) || "meta".equals(k)
                        || "immutable".equals(k) || "paramsRelPath".equals(k)) {
                    continue;
                }
                if (e.getValue() instanceof Map) continue;
                params.put(k, e.getValue());
            }
        }

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, String> toApply = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            ParamMeta meta = findMeta(MODIFIABLE, key);
            if (meta == null) {
                skipped.add(key);
                continue;
            }
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue());
            if (meta.sensitive && value.trim().isEmpty()) {
                skipped.add(key + "（敏感值留空）");
                continue;
            }
            toApply.put(key, value);
            applied.add(key);
        }

        if (toApply.isEmpty()) {
            out.put("success", false);
            out.put("error", "没有可应用的参数");
            out.put("applied", applied);
            out.put("skipped", skipped);
            return out;
        }

        ConfigLoader.setProperties(toApply);
        log.info("系统参数导入完成：应用 {} 项，跳过 {} 项", applied.size(), skipped.size());

        out.put("success", true);
        out.put("applied", applied);
        out.put("skipped", skipped);
        out.put("message", "已写入 system-params.json；重启后生效的参数请重启服务");
        return out;
    }

    // ==================== 内部辅助 ====================

    private Map<String, String> collectCurrentParams() {
        Map<String, String> params = new LinkedHashMap<>();
        for (ParamMeta m : MODIFIABLE) {
            String v = readValue(m);
            params.put(m.key, m.sensitive ? "" : (v == null ? "" : v));
        }
        return params;
    }

    private List<Map<String, Object>> buildItems(List<ParamMeta> metas, boolean editable) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ParamMeta m : metas) {
            String raw = readValue(m);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", m.key);
            item.put("description", m.description);
            item.put("file", m.file);
            item.put("type", m.type);
            item.put("options", m.options);
            item.put("editable", editable);
            item.put("sensitive", m.sensitive);
            item.put("needRestart", m.needRestart);

            if (m.sensitive) {
                item.put("value", editable ? "" : (isEmpty(raw) ? "" : "******"));
            } else {
                item.put("value", raw == null ? "" : raw);
            }
            list.add(item);
        }
        return list;
    }

    private String readValue(ParamMeta m) {
        if (m.fromSystemProperty) return System.getProperty(m.key);
        return ConfigLoader.getProperty(m.key);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private ParamMeta findMeta(List<ParamMeta> list, String key) {
        for (ParamMeta m : list) {
            if (m.key.equals(key)) return m;
        }
        return null;
    }

    private static boolean isSensitive(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        return k.contains("password") || k.contains("passwd") || k.contains("token")
                || k.contains("secret") || k.endsWith(".key") || k.contains("api.key");
    }

    private Map<String, Object> fail(Map<String, Object> out, String msg) {
        out.put("success", false);
        out.put("error", msg);
        return out;
    }

    private static class ParamMeta {
        String key;
        String description;
        String file;
        String type;
        List<String> options = new ArrayList<>();
        boolean editable;
        boolean sensitive;
        boolean needRestart;
        boolean fromSystemProperty;
    }
}