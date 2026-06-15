// 文件路径: src/main/java/com/omni/business/assistance/questionbank/service/QuestionBankService.java
package com.omni.business.assistance.questionbank.service;

import com.omni.common.excel.ExcelUtil;
import com.omni.framework.codestats.config.ConfigLoader;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class QuestionBankService {
    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);

    private static final String DEFAULT_ROOT_DIR;
    private static final String DEFAULT_OUTPUT_FILE;
    private static final String DEFAULT_KEYWORD;
    private static final String DEFAULT_NAME_PREFIXES;

    static {
        String rootDir = "D:/题库";
        String outputFile = System.getProperty("java.io.tmpdir") + "/merged_questionbank.txt";
        String keyword = "";
        String namePrefixes = "";

        try {
            Map<String, Object> qbConfig = ConfigLoader.getModuleConfig("questionbank");
            Map<String, Object> defaults = (Map<String, Object>) qbConfig.getOrDefault("default", new HashMap<>());

            rootDir = (String) defaults.getOrDefault("rootDir", rootDir);
            outputFile = (String) defaults.getOrDefault("outputFile", outputFile);
            keyword = (String) defaults.getOrDefault("keyword", keyword);
            namePrefixes = (String) defaults.getOrDefault("namePrefixes", namePrefixes);
        } catch (Exception e) {
            log.error("加载题库配置失败，使用默认值", e);
        }

        DEFAULT_ROOT_DIR = rootDir;
        DEFAULT_OUTPUT_FILE = replaceSystemProperties(outputFile);
        DEFAULT_KEYWORD = keyword;
        DEFAULT_NAME_PREFIXES = namePrefixes;

        log.info("题库服务初始化完成，默认根目录: {}, 输出文件: {}, 关键字: {}, 前缀: {}",
                DEFAULT_ROOT_DIR, DEFAULT_OUTPUT_FILE, DEFAULT_KEYWORD, DEFAULT_NAME_PREFIXES);
    }

    private static String replaceSystemProperties(String value) {
        if (value == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{(.*?)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String propName = matcher.group(1);
            String propValue = System.getProperty(propName);
            if (propValue == null) propValue = System.getenv(propName);
            if (propValue == null) propValue = "";
            matcher.appendReplacement(sb, propValue.replace("\\", "\\\\"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<String> mergedLines = new ArrayList<>();
    private Path currentMergedFile = null;

    // 预览缓存
    private List<String> previewColumns = null;
    private List<Map<String, String>> previewRows = null;

    public String getDefaultRootDir() { return DEFAULT_ROOT_DIR; }
    public String getDefaultOutputFile() { return DEFAULT_OUTPUT_FILE; }
    public String getDefaultKeyword() { return DEFAULT_KEYWORD; }
    public String getDefaultNamePrefixes() { return DEFAULT_NAME_PREFIXES; }

    // ========== 扫描文件（支持 .xls, .xlsx, .txt） ==========
    private List<String> scanMergeableFiles(String rootDir, String keyword, String namePrefixes) throws IOException {
        Path start = Paths.get(rootDir).normalize();
        if (!Files.exists(start)) throw new IllegalArgumentException("目录不存在: " + rootDir);

        List<String> prefixList;
        if (namePrefixes != null && !namePrefixes.trim().isEmpty()) {
            prefixList = Arrays.stream(namePrefixes.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toList());
        } else {
            prefixList = null;
        }

        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".txt");
                    })
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        boolean matched = false;
                        if (prefixList != null && !prefixList.isEmpty()) {
                            for (String prefix : prefixList) {
                                if (fileName.startsWith(prefix)) {
                                    matched = true;
                                    break;
                                }
                            }
                        } else if (keyword != null && !keyword.isEmpty()) {
                            if (fileName.toLowerCase().contains(keyword.toLowerCase())) {
                                matched = true;
                            }
                        } else {
                            matched = true;
                        }
                        if (matched) {
                            result.add(p.toAbsolutePath().toString());
                        }
                    });
        }
        return result;
    }

    // ========== 公开的扫描接口（兼容原有 Controller 调用） ==========
    public List<String> scanExcelFiles(String rootDir, String keyword, String namePrefixes) throws IOException {
        // 委托给统一扫描方法，返回所有支持的文件（包括 .txt）
        return scanMergeableFiles(rootDir, keyword, namePrefixes);
    }

    // ========== 统一合并入口（禁止混用 Excel 和文本文件） ==========
    public Map<String, Object> mergeFiles(String rootDir, String keyword, String namePrefixes, String outputFile) throws IOException {
        List<String> files = scanMergeableFiles(rootDir, keyword, namePrefixes);
        if (files.isEmpty()) {
            throw new RuntimeException("未找到匹配的文件（支持 .xls/.xlsx/.txt）");
        }

        boolean hasExcel = files.stream().anyMatch(f -> f.toLowerCase().endsWith(".xls") || f.toLowerCase().endsWith(".xlsx"));
        boolean hasTxt = files.stream().anyMatch(f -> f.toLowerCase().endsWith(".txt"));
        if (hasExcel && hasTxt) {
            throw new RuntimeException("不能同时合并 Excel 和纯文本文件，请分开操作");
        }

        if (hasExcel) {
            return mergeExcelFiles(files, outputFile);
        } else {
            return mergeTextFiles(files, outputFile);
        }
    }

    // ---------- Excel 合并 ----------
    public Map<String, Object> mergeExcelFiles(List<String> excelFiles, String outputFile) throws IOException {
        Path outPath = Paths.get(outputFile).normalize();
        if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());

        List<String> allLines = new ArrayList<>();
        allLines.add("// 题库合并文件 - 生成时间: " + new Date());
        allLines.add("// 匹配模式: " + (DEFAULT_NAME_PREFIXES.isEmpty() ? "模糊匹配: " + DEFAULT_KEYWORD : "前缀匹配: " + DEFAULT_NAME_PREFIXES));
        allLines.add("// 源文件列表: " + String.join(", ", excelFiles));
        allLines.add("");

        int totalRows = 0;
        for (String filePath : excelFiles) {
            try {
                List<String> lines = ExcelUtil.parseExcelToCsvLines(new File(filePath));
                allLines.addAll(lines);
                totalRows += lines.size();
            } catch (Exception e) {
                log.error("解析 Excel 失败: " + filePath, e);
                allLines.add("// 解析失败: " + filePath + " - " + e.getMessage());
            }
        }

        Files.write(outPath, allLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        currentMergedFile = outPath;
        mergedLines = allLines;
        previewColumns = null;
        previewRows = null;

        Map<String, Object> result = new HashMap<>();
        result.put("mergedFile", outPath.toAbsolutePath().toString());
        result.put("totalLines", totalRows);
        result.put("fileCount", excelFiles.size());
        result.put("files", excelFiles);
        return result;
    }

    // ---------- 文本文件合并 ----------
    private List<String> readTextFileAsCsvLines(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String escaped = line.replace("\"", "\"\"");
                lines.add("\"" + escaped + "\"");
            }
        }
        return lines;
    }

    private Map<String, Object> mergeTextFiles(List<String> textFiles, String outputFile) throws IOException {
        Path outPath = Paths.get(outputFile).normalize();
        if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());

        List<String> allLines = new ArrayList<>();
        allLines.add("// 文本文件合并 - 生成时间: " + new Date());
        allLines.add("Content");   // 单列表头

        int totalRows = 0;
        for (String filePath : textFiles) {
            Path path = Paths.get(filePath);
            allLines.add("// 文件: " + path.getFileName());
            List<String> fileLines = readTextFileAsCsvLines(path);
            allLines.addAll(fileLines);
            totalRows += fileLines.size();
        }

        Files.write(outPath, allLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        currentMergedFile = outPath;
        mergedLines = allLines;
        previewColumns = null;
        previewRows = null;

        Map<String, Object> result = new HashMap<>();
        result.put("mergedFile", outPath.toAbsolutePath().toString());
        result.put("totalLines", totalRows);
        result.put("fileCount", textFiles.size());
        result.put("files", textFiles);
        return result;
    }

    // ========== 按关键词搜索 ==========
    public List<Map<String, String>> searchByKeyword(String keyword) {
        if (mergedLines == null || mergedLines.isEmpty()) {
            return Collections.emptyList();
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerKeyword = keyword.toLowerCase();
        List<Map<String, String>> matches = new ArrayList<>();
        int lineNo = 0;
        for (String line : mergedLines) {
            lineNo++;
            if (line.toLowerCase().contains(lowerKeyword)) {
                Map<String, String> item = new HashMap<>();
                item.put("lineNumber", String.valueOf(lineNo));
                item.put("content", line);
                matches.add(item);
            }
        }
        return matches;
    }

    public String getCurrentMergedFile() {
        return currentMergedFile == null ? null : currentMergedFile.toString();
    }

    // ========== 预览数据加载 ==========
    private synchronized void ensurePreviewDataLoaded() throws IOException {
        if (previewColumns != null && previewRows != null) {
            return;
        }
        if (mergedLines == null || mergedLines.isEmpty()) {
            throw new IOException("没有合并文件数据，请先执行合并操作");
        }

        List<String> dataLines = new ArrayList<>();
        for (String line : mergedLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;
            dataLines.add(line);
        }
        if (dataLines.isEmpty()) {
            throw new IOException("合并文件中无有效数据");
        }

        String headerLine = dataLines.get(0);
        List<String> columns = parseCsvLine(headerLine);
        previewColumns = columns;

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < dataLines.size(); i++) {
            List<String> values = parseCsvLine(dataLines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < columns.size(); j++) {
                String colName = columns.get(j);
                String val = (j < values.size()) ? values.get(j) : "";
                row.put(colName, val);
            }
            rows.add(row);
        }
        previewRows = rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (ch == ',' && !inQuote) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        result.add(sb.toString());
        return result;
    }

    // ========== 获取预览数据（分页 + 过滤） ==========
    public Map<String, Object> getPreviewData(String keyword, int page, int pageSize) throws IOException {
        ensurePreviewDataLoaded();
        if (previewRows == null || previewColumns == null) {
            throw new IOException("预览数据未就绪");
        }

        List<Map<String, String>> filteredRows = previewRows;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKw = keyword.trim().toLowerCase();
            filteredRows = previewRows.stream()
                    .filter(row -> row.values().stream().anyMatch(val -> val != null && val.toLowerCase().contains(lowerKw)))
                    .collect(Collectors.toList());
        }

        int total = filteredRows.size();
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        List<Map<String, String>> pageRows = (start < end) ? filteredRows.subList(start, end) : Collections.emptyList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("columns", previewColumns);
        result.put("rows", pageRows);
        return result;
    }
}