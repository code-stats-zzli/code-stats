// 文件路径: src/main/java/com/omni/business/assistance/questionbank/service/QuestionBankService.java
package com.omni.business.assistance.questionbank.service;

import com.omni.common.cache.Cache;
import com.omni.common.cache.CacheFactory;
import com.omni.business.common.excel.ExcelUtil;
import com.omni.framework.codestats.config.ConfigLoader;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
        String rootDir = null;
        String outputFile = null;
        String keyword = null;
        String namePrefixes = null;

        try {
            Map<String, Object> qbConfig = ConfigLoader.getModuleConfig("questionbank");
            Map<String, Object> defaults = (Map<String, Object>) qbConfig.getOrDefault("default", new HashMap<>());

            rootDir = (String) defaults.get("rootDir");
            outputFile = (String) defaults.get("outputFile");
            keyword = (String) defaults.get("keyword");
            namePrefixes = (String) defaults.get("namePrefixes");

            // 强制配置检查
            if (rootDir == null || rootDir.trim().isEmpty()) {
                throw new RuntimeException("questionbank.default.rootDir 未配置，请在 config.yaml 中设置");
            }
            if (outputFile == null || outputFile.trim().isEmpty()) {
                throw new RuntimeException("questionbank.default.outputFile 未配置，请在 config.yaml 中设置");
            }
        } catch (Exception e) {
            log.error("加载题库配置失败", e);
            throw new RuntimeException("题库配置加载失败，请检查 config.yaml 中的 questionbank 配置", e);
        }

        DEFAULT_ROOT_DIR = rootDir;
        DEFAULT_OUTPUT_FILE = replaceSystemProperties(outputFile);
        DEFAULT_KEYWORD = keyword != null ? keyword : "";
        DEFAULT_NAME_PREFIXES = namePrefixes != null ? namePrefixes : "";

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
    private final Object mergeLock = new Object();

    private final Cache<String, PreviewCacheData> previewCache = CacheFactory.createDefaultCache();

    private static class PreviewCacheData implements Serializable {
        private static final long serialVersionUID = 1L;
        List<String> columns;
        List<Map<String, String>> rows;
        long cacheTime;
        int totalRows;
    }

    public String getDefaultRootDir() { return DEFAULT_ROOT_DIR; }
    public String getDefaultOutputFile() { return DEFAULT_OUTPUT_FILE; }
    public String getDefaultKeyword() { return DEFAULT_KEYWORD; }
    public String getDefaultNamePrefixes() { return DEFAULT_NAME_PREFIXES; }

    // ==================== 自动合并保障 ====================

    private void ensureMergedFile() {
        if (currentMergedFile != null && Files.exists(currentMergedFile)) {
            return;
        }
        synchronized (mergeLock) {
            if (currentMergedFile != null && Files.exists(currentMergedFile)) {
                return;
            }
            log.info("未检测到合并文件，自动执行合并...");
            try {
                Map<String, Object> mergeResult = mergeFiles(
                        DEFAULT_ROOT_DIR,
                        DEFAULT_KEYWORD,
                        DEFAULT_NAME_PREFIXES,
                        DEFAULT_OUTPUT_FILE
                );
                log.info("自动合并完成，合并文件: {}", mergeResult.get("mergedFile"));
            } catch (Exception e) {
                throw new RuntimeException("自动合并失败，请检查配置或手动合并。原因: " + e.getMessage(), e);
            }
        }
    }

    // ==================== 扫描文件 ====================
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

    public List<String> scanExcelFiles(String rootDir, String keyword, String namePrefixes) throws IOException {
        return scanMergeableFiles(rootDir, keyword, namePrefixes);
    }

    // ==================== 统一合并入口 ====================
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

        Map<String, Object> mergeResult;
        if (hasExcel) {
            mergeResult = mergeExcelFiles(files, outputFile);
        } else {
            mergeResult = mergeTextFiles(files, outputFile);
        }

        buildPreviewCacheFromMergedLines();
        return mergeResult;
    }

    private Map<String, Object> mergeExcelFiles(List<String> excelFiles, String outputFile) throws IOException {
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

        Map<String, Object> result = new HashMap<>();
        result.put("mergedFile", outPath.toAbsolutePath().toString());
        result.put("totalLines", totalRows);
        result.put("fileCount", excelFiles.size());
        result.put("files", excelFiles);
        return result;
    }

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
        allLines.add("Content");

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

        Map<String, Object> result = new HashMap<>();
        result.put("mergedFile", outPath.toAbsolutePath().toString());
        result.put("totalLines", totalRows);
        result.put("fileCount", textFiles.size());
        result.put("files", textFiles);
        return result;
    }

    // ==================== 按关键词搜索（只返回第一条匹配行） ====================
    public Map<String, Object> searchByKeyword(String keyword) throws IOException {
        ensureMergedFile();

        if (currentMergedFile == null) {
            log.warn("搜索时合并文件为空");
            Map<String, Object> empty = new HashMap<>();
            empty.put("columns", Collections.emptyList());
            empty.put("row", null);
            return empty;
        }
        String cacheKey = currentMergedFile.toAbsolutePath().toString();
        PreviewCacheData cached = previewCache.get(cacheKey);
        if (cached == null) {
            buildPreviewCacheFromMergedLines();
            cached = previewCache.get(cacheKey);
            if (cached == null) {
                log.warn("预览缓存构建失败");
                Map<String, Object> empty = new HashMap<>();
                empty.put("columns", Collections.emptyList());
                empty.put("row", null);
                return empty;
            }
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("columns", cached.columns);
            result.put("row", cached.rows.isEmpty() ? null : cached.rows.get(0));
            return result;
        }
        String lowerKeyword = keyword.trim().toLowerCase();
        Map<String, String> matchedRow = null;

        for (Map<String, String> row : cached.rows) {
            boolean match = row.values().stream().anyMatch(val -> val != null && val.toLowerCase().contains(lowerKeyword));
            if (match) {
                matchedRow = row;
                break;
            }
        }
        log.info("搜索关键词 '{}'，找到匹配: {}", keyword, matchedRow != null);

        Map<String, Object> result = new HashMap<>();
        result.put("columns", cached.columns);
        result.put("row", matchedRow);
        return result;
    }

    public String getCurrentMergedFile() {
        return currentMergedFile == null ? null : currentMergedFile.toString();
    }

    // ==================== 构建预览缓存（供预览和搜索共用） ====================
    private void buildPreviewCacheFromMergedLines() throws IOException {
        if (currentMergedFile == null || mergedLines == null || mergedLines.isEmpty()) {
            return;
        }
        List<String> dataLines = new ArrayList<>();
        for (String line : mergedLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;
            dataLines.add(line);
        }
        if (dataLines.isEmpty()) {
            return;
        }
        String headerLine = dataLines.get(0);
        List<String> columns = parseCsvLine(headerLine);
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

        PreviewCacheData cacheData = new PreviewCacheData();
        cacheData.columns = columns;
        cacheData.rows = rows;
        cacheData.totalRows = rows.size();
        cacheData.cacheTime = System.currentTimeMillis();

        String cacheKey = currentMergedFile.toAbsolutePath().toString();
        previewCache.put(cacheKey, cacheData, 24 * 60 * 60 * 1000L);
        log.info("预览数据已缓存，key={}, 行数={}", cacheKey, rows.size());
    }

    // ==================== 获取预览数据（分页） ====================
    public Map<String, Object> getPreviewData(String keyword, int page, int pageSize) throws IOException {
        ensureMergedFile();

        if (currentMergedFile == null) {
            throw new IOException("没有合并文件数据，请先执行合并操作");
        }
        String cacheKey = currentMergedFile.toAbsolutePath().toString();
        PreviewCacheData cached = previewCache.get(cacheKey);
        if (cached == null) {
            buildPreviewCacheFromMergedLines();
            cached = previewCache.get(cacheKey);
            if (cached == null) {
                throw new IOException("预览数据构建失败");
            }
        }

        List<Map<String, String>> filteredRows = cached.rows;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKw = keyword.trim().toLowerCase();
            filteredRows = cached.rows.stream()
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
        result.put("columns", cached.columns);
        result.put("rows", pageRows);
        return result;
    }

    // ==================== 导出 Excel ====================
    public void exportToExcel(String keyword, OutputStream outputStream) throws IOException {
        if (currentMergedFile == null) {
            throw new IOException("没有合并文件数据，请先执行合并操作");
        }
        String cacheKey = currentMergedFile.toAbsolutePath().toString();
        PreviewCacheData cached = previewCache.get(cacheKey);
        if (cached == null) {
            buildPreviewCacheFromMergedLines();
            cached = previewCache.get(cacheKey);
            if (cached == null) {
                throw new IOException("预览数据未就绪");
            }
        }

        List<Map<String, String>> filteredRows = cached.rows;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKw = keyword.trim().toLowerCase();
            filteredRows = cached.rows.stream()
                    .filter(row -> row.values().stream().anyMatch(val -> val != null && val.toLowerCase().contains(lowerKw)))
                    .collect(Collectors.toList());
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("题库数据");

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < cached.columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cached.columns.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Map<String, String> rowData : filteredRows) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < cached.columns.size(); i++) {
                    String colName = cached.columns.get(i);
                    String value = rowData.getOrDefault(colName, "");
                    row.createCell(i).setCellValue(value);
                }
            }

            for (int i = 0; i < cached.columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
        }
    }

    // ==================== CSV 行解析（支持引号、转义） ====================
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
}