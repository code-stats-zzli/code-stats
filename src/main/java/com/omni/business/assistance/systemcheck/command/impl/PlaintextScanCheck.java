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

    private static final Pattern RE_ASSIGN = Pattern.compile(
            "(?i)(password|passwd|pwd|access[_]?key|api[_]?key|private[_]?key|密钥|密码|口令|账号|账户)\\s*[:=：]\\s*\\S+");
    private static final Set<String> EXT_TEXT = new HashSet<>(Arrays.asList(
            ".txt", ".md", ".csv", ".env", ".gitconfig", ".npmrc", ".properties",
            ".yml", ".yaml", ".ini", ".conf", ".config", ".toml", ".cnf", ".xml",
            ".json", ".sql", ".sh", ".bat", ".ps1", ".gradle"));
    private static final Set<String> EXT_DOC = new HashSet<>(Arrays.asList(
            ".doc", ".xls", ".docx", ".xlsx", ".pdf"));

    @Override public String getId() { return "plaintext-scan"; }
    @Override public String getName() { return "明文密码扫描"; }
    @Override public int getEstimatedSeconds(CheckConfig config) { return 30; }

    @Override
    public List<Map<String, Object>> getConfigFields(CheckConfig config) {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> f1 = new LinkedHashMap<>();
        f1.put("key", "maxFileSize");
        f1.put("label", "最大文本文件大小(字节)");
        f1.put("type", "number");
        f1.put("value", config.getPlaintextScanMaxFileSize());
        f1.put("hint", "超过该大小的文本文件会跳过（默认 15728640 = 15MB）");
        list.add(f1);

        Map<String, Object> f2 = new LinkedHashMap<>();
        f2.put("key", "maxDocSize");
        f2.put("label", "最大文档大小(字节)");
        f2.put("type", "number");
        f2.put("value", config.getPlaintextScanMaxDocSize());
        f2.put("hint", "超过该大小的 Office/PDF 只提示不解析（默认 3145728 = 3MB）");
        list.add(f2);

        return list;
    }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        List<String> docFiles = new ArrayList<>();

        String home = System.getProperty("user.home", "");
        Path rootPath = Paths.get(home);
        if (!Files.exists(rootPath)) {
            list.add(new Finding("明文密码", "配置文件扫描", "合规", "信息", "主目录不可访问"));
            return list;
        }

        long maxFile = ctx.getConfig().getPlaintextScanMaxFileSize();
        long maxDoc = ctx.getConfig().getPlaintextScanMaxDocSize();

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase();
                    if (name.equals("node_modules") || name.equals(".git") || name.equals(".idea")
                            || name.equals(".gradle") || name.equals("target") || name.equals("build")
                            || name.equals(".m2") || name.equals(".cache") || name.equals(".npm")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String name = file.getFileName().toString().toLowerCase();
                    long len = attrs.size();
                    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
                    try {
                        if (EXT_TEXT.contains(ext)) {
                            if (len > maxFile) return FileVisitResult.CONTINUE;
                            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                            Matcher m = RE_ASSIGN.matcher(content);
                            int cnt = 0;
                            while (m.find() && cnt < 5) {
                                String g = m.group();
                                String val = g.replaceFirst("^[^:=：]*[:=：]\\s*", "");
                                if (isPlaceholder(val)) { cnt++; continue; }
                                int start = m.start();
                                int lineStart = content.lastIndexOf('\n', start);
                                int lineEnd = content.indexOf('\n', start);
                                if (lineEnd < 0) lineEnd = content.length();
                                String line = content.substring(lineStart + 1, lineEnd).trim();
                                if (!line.matches("(?s).*(弱口令|口令字典|Telnet|README|扫描|检测|匹配|说明|密码规则).*")) {
                                    hits.add(file + " : " + line);
                                }
                                cnt++;
                            }
                        } else if (EXT_DOC.contains(ext)) {
                            if (len > maxDoc) return FileVisitResult.CONTINUE;
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

    private boolean isPlaceholder(String v) {
        if (v == null || v.isEmpty()) return false;
        char c = v.charAt(0);
        char close = c == '{' ? '}' : c == '<' ? '>' : c == '[' ? ']' : 0;
        return close != 0 && v.indexOf(close) > 0;
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
}