package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaintextScanCheck extends AbstractSystemCheckCommand {

    @Override public String getId() { return "plaintext-scan"; }
    @Override public String getName() { return "明文密码扫描"; }
    @Override public int getEstimatedSeconds(CheckConfig config) { return 30; }

    @Override
    public List<Map<String, Object>> getConfigFields(CheckConfig config) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(field("maxFileSize", "最大文本文件大小(字节)", "number",
                config.getModuleLong("plaintext-scan", "maxFileSize", 15L * 1024 * 1024),
                "超过此大小的文本文件跳过（默认 15MB）"));
        list.add(field("maxDocSize", "最大文档大小(字节)", "number",
                config.getModuleLong("plaintext-scan", "maxDocSize", 3L * 1024 * 1024),
                "超过此大小的 Office/PDF 仅提示不解析（默认 3MB）"));
        list.add(fieldList("passwordKeywords", "密码关键字",
                config.getModuleList("plaintext-scan", "passwordKeywords", defaultKeywords()),
                "命中这些关键字视为潜在密码赋值；逗号分隔"));
        list.add(fieldList("textExtensions", "文本文件扩展名",
                config.getModuleList("plaintext-scan", "textExtensions", defaultTextExts()),
                "需要扫描内容的文本类扩展名；逗号分隔（含前导点）"));
        list.add(fieldList("docExtensions", "文档扩展名",
                config.getModuleList("plaintext-scan", "docExtensions", defaultDocExts()),
                "需要关键词检测的文档扩展名；逗号分隔"));
        list.add(fieldList("skipDirs", "跳过目录",
                config.getModuleList("plaintext-scan", "skipDirs", defaultSkipDirs()),
                "这些目录名整棵子树跳过；逗号分隔"));
        list.add(fieldList("skipKeywords", "跳过关键词",
                config.getModuleList("plaintext-scan", "skipKeywords", defaultSkipKeywords()),
                "行内容包含这些关键词时不计入命中；逗号分隔"));
        list.add(field("assignRegex", "密码赋值正则", "text",
                config.getModuleString("plaintext-scan", "assignRegex", defaultAssignRegex()),
                "Java 正则；注意转义。修改请谨慎"));
        list.add(fieldList("placeholderBrackets", "占位符括号",
                config.getModuleList("plaintext-scan", "placeholderBrackets",
                        Arrays.asList("{}", "<>", "()")),
                "以这些括号包裹的值视为占位符（如 {password}），不告警"));
        return list;
    }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) throws Exception {
        List<Finding> list = new ArrayList<>();
        CheckConfig cfg = ctx.getConfig();
        List<String> hits = new ArrayList<>();
        List<String> docFiles = new ArrayList<>();

        String home = System.getProperty("user.home", "");
        Path rootPath = Paths.get(home);
        if (!Files.exists(rootPath)) {
            list.add(new Finding("明文密码", "配置文件扫描", "合规", "信息", "主目录不可访问"));
            return list;
        }

        long maxFile = cfg.getModuleLong("plaintext-scan", "maxFileSize", 15L * 1024 * 1024);
        long maxDoc = cfg.getModuleLong("plaintext-scan", "maxDocSize", 3L * 1024 * 1024);
        Set<String> textExts = new HashSet<>(cfg.getModuleList("plaintext-scan", "textExtensions", defaultTextExts()));
        Set<String> docExts = new HashSet<>(cfg.getModuleList("plaintext-scan", "docExtensions", defaultDocExts()));
        Set<String> skipDirs = new HashSet<>(cfg.getModuleList("plaintext-scan", "skipDirs", defaultSkipDirs()));
        List<String> skipKws = cfg.getModuleList("plaintext-scan", "skipKeywords", defaultSkipKeywords());
        List<String> placeholders = cfg.getModuleList("plaintext-scan", "placeholderBrackets",
                Arrays.asList("{}", "<>", "()"));
        String assignRegex = cfg.getModuleString("plaintext-scan", "assignRegex", defaultAssignRegex());

        Pattern assignPattern;
        try {
            assignPattern = Pattern.compile(assignRegex);
        } catch (Exception e) {
            list.add(new Finding("明文密码", "正则配置错误", "警告", "低",
                    "assignRegex 无法编译：" + e.getMessage() + "，使用默认"));
            assignPattern = Pattern.compile(defaultAssignRegex());
        }

        final Pattern finalPattern = assignPattern;
        final List<String> finalSkipKws = skipKws;
        final List<String> finalPlaceholders = placeholders;

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (ctx.isCancelled()) return FileVisitResult.TERMINATE;
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase();
                    if (skipDirs.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (ctx.isCancelled()) return FileVisitResult.TERMINATE;
                    String name = file.getFileName().toString().toLowerCase();
                    long len = attrs.size();
                    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
                    try {
                        if (textExts.contains(ext)) {
                            if (len > maxFile) return FileVisitResult.CONTINUE;
                            // ★ 新增：上报当前正在检查的文件（仅真正会被读取的文件才上报）
                            ctx.reportProgress(file.getFileName().toString());

                            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                            Matcher m = finalPattern.matcher(content);
                            int cnt = 0;
                            while (m.find() && cnt < 5) {
                                String g = m.group();
                                String val = g.replaceFirst("^[^:=：]*[:=：]\\s*", "");
                                if (isPlaceholder(val, finalPlaceholders)) { cnt++; continue; }
                                int start = m.start();
                                int lineStart = content.lastIndexOf('\n', start);
                                int lineEnd = content.indexOf('\n', start);
                                if (lineEnd < 0) lineEnd = content.length();
                                String line = content.substring(lineStart + 1, lineEnd).trim();
                                boolean skip = false;
                                for (String kw : finalSkipKws) {
                                    if (line.contains(kw)) { skip = true; break; }
                                }
                                if (!skip) hits.add(file + " : " + line);
                                cnt++;
                            }
                        } else if (docExts.contains(ext)) {
                            if (len > maxDoc) return FileVisitResult.CONTINUE;
                            // ★ 新增：上报当前正在检查的文档
                            ctx.reportProgress(file.getFileName().toString());

                            byte[] b = Files.readAllBytes(file);
                            String text = extractPrintable(b);
                            if (text.matches("(?i).*(password|passwd|pwd|密码|口令).*")) {
                                docFiles.add(file.toString());
                            }
                        }
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, java.io.IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {}

        if (ctx.isCancelled()) {
            list.add(new Finding("明文密码", "扫描已取消", "信息", "信息", "用户中止扫描"));
            return list;
        }

        if (hits.isEmpty()) {
            list.add(new Finding("明文密码", "配置文件明文密码扫描", "合规", "信息", "未发现疑似明文密码"));
        } else {
            for (String h : hits) {
                list.add(new Finding("明文密码", "配置文件疑似明文凭据", "不合规", "高", h));
            }
        }
        if (!docFiles.isEmpty()) {
            list.add(new Finding("明文密码", "文档含账号/密码关键词", "警告", "中",
                    "共 " + docFiles.size() + " 个: " + String.join("; ", docFiles)));
        }
        return list;
    }

    private boolean isPlaceholder(String v, List<String> placeholders) {
        if (v == null || v.isEmpty()) return false;
        for (String bracket : placeholders) {
            if (bracket.length() != 2) continue;
            char open = bracket.charAt(0);
            char close = bracket.charAt(1);
            if (v.charAt(0) == open && v.indexOf(close) > 0) return true;
        }
        return false;
    }

    private String extractPrintable(byte[] b) {
        StringBuilder sb = new StringBuilder();
        int run = 0;
        for (byte x : b) {
            if (x >= 32 && x <= 126) {
                if (run < 200) sb.append((char) x);
                run++;
            } else {
                if (run >= 5) sb.append(' ');
                run = 0;
                if (sb.length() > 400000) break;
            }
        }
        return sb.toString();
    }

    // 默认值
    private static List<String> defaultKeywords() {
        return Arrays.asList("password", "passwd", "pwd", "access_key", "api_key",
                "private_key", "密钥", "密码", "口令", "账号", "账户", "secret", "token", "credential");
    }
    private static List<String> defaultTextExts() {
        return Arrays.asList(".txt", ".md", ".csv", ".env", ".gitconfig", ".npmrc",
                ".properties", ".yml", ".yaml", ".ini", ".conf", ".config", ".toml",
                ".cnf", ".xml", ".json", ".sql", ".sh", ".bat", ".ps1", ".gradle");
    }
    private static List<String> defaultDocExts() {
        return Arrays.asList(".doc", ".xls", ".docx", ".xlsx", ".pdf");
    }
    private static List<String> defaultSkipDirs() {
        return Arrays.asList("node_modules", ".git", ".idea", ".gradle",
                "target", "build", ".m2", ".cache", ".npm", ".vscode");
    }
    private static List<String> defaultSkipKeywords() {
        return Arrays.asList("弱口令", "口令字典", "Telnet", "README", "扫描", "检测",
                "匹配", "说明", "密码规则", "示例", "example", "sample", "placeholder");
    }
    private static String defaultAssignRegex() {
        return "(?i)(password|passwd|pwd|access[_]?key|api[_]?key|private[_]?key|密钥|密码|口令|账号|账户|secret|token|credential)\\s*[:=：]\\s*\\S+";
    }

    private Map<String, Object> field(String key, String label, String type, Object value, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key); m.put("label", label); m.put("type", type);
        m.put("value", value); m.put("hint", hint);
        return m;
    }
    private Map<String, Object> fieldList(String key, String label, List<String> value, String hint) {
        return field(key, label, "list", value, hint);
    }
}