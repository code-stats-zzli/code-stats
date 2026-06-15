package com.omni.business.assistance.questionbank.service;


import com.omni.common.excel.ExcelUtil;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class QuestionBankService {
    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);

    // 缓存合并后的所有行（用于搜索）
    private List<String> mergedLines = new ArrayList<>();
    private Path currentMergedFile = null;

    /**
     * 扫描目录，模糊匹配 Excel 文件
     * @param rootDir 根目录
     * @param namePattern 文件名模糊匹配（如 "题库"、"*exam*"）
     * @return 匹配的文件路径列表
     */
    public List<String> scanExcelFiles(String rootDir, String namePattern) throws IOException {
        Path start = Paths.get(rootDir).normalize();
        if (!Files.exists(start)) throw new IllegalArgumentException("目录不存在: " + rootDir);

        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".xls") || p.toString().toLowerCase().endsWith(".xlsx"))
                    .filter(p -> namePattern == null || namePattern.isEmpty() || p.getFileName().toString().toLowerCase().contains(namePattern.toLowerCase()))
                    .forEach(p -> result.add(p.toAbsolutePath().toString()));
        }
        return result;
    }

    /**
     * 合并匹配的 Excel 文件，输出到指定 TXT 文件（CSV 格式）
     * @param rootDir 根目录
     * @param namePattern 文件名模糊匹配
     * @param outputFile 输出 TXT 文件路径
     * @return 合并信息（文件路径、总行数）
     */
    public Map<String, Object> mergeExcelFiles(String rootDir, String namePattern, String outputFile) throws IOException {
        List<String> excelFiles = scanExcelFiles(rootDir, namePattern);
        if (excelFiles.isEmpty()) {
            throw new RuntimeException("未找到匹配的 Excel 文件");
        }

        Path outPath = Paths.get(outputFile).normalize();
        if (outPath.getParent() != null) Files.createDirectories(outPath.getParent());

        List<String> allLines = new ArrayList<>();
        allLines.add("// 题库合并文件 - 生成时间: " + new Date());
        allLines.add("// 匹配模式: " + namePattern);
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

        ExcelUtil.writeCsvLinesToFile(outPath, allLines);
        currentMergedFile = outPath;
        mergedLines = allLines; // 缓存所有行用于搜索

        Map<String, Object> result = new HashMap<>();
        result.put("mergedFile", outPath.toString());
        result.put("totalLines", totalRows);
        result.put("fileCount", excelFiles.size());
        result.put("files", excelFiles);
        return result;
    }

    /**
     * 根据题目关键词在合并后的内容中搜索
     * @param keyword 关键词（题目中的文字）
     * @return 匹配的行及其上下文（整行）
     */
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

    /**
     * 获取当前合并文件路径（用于前端展示）
     */
    public String getCurrentMergedFile() {
        return currentMergedFile == null ? null : currentMergedFile.toString();
    }
}