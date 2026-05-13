package com.codestats.lib.codestats.statistics;


import com.codestats.lib.codestats.core.FileInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;



// 行数统计（支持排除空行/注释）
public class LineCountStrategy implements StatisticStrategy {
    private final boolean excludeCommentsAndBlanks;

    public LineCountStrategy(boolean excludeCommentsAndBlanks) {
        this.excludeCommentsAndBlanks = excludeCommentsAndBlanks;
    }

    @Override
    public void analyze(Path file, FileInfo info) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            long total = 0, code = 0, comment = 0, blank = 0;
            boolean inBlockComment = false;
            while ((line = reader.readLine()) != null) {
                total++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    blank++;
                    continue;
                }
                // 简化的注释检测（实际项目需要更严谨）
                if (excludeCommentsAndBlanks) {
                    if (trimmed.startsWith("//")) {
                        comment++;
                    } else if (trimmed.startsWith("/*")) {
                        comment++;
                        inBlockComment = true;
                    } else if (trimmed.endsWith("*/") && inBlockComment) {
                        comment++;
                        inBlockComment = false;
                    } else if (inBlockComment) {
                        comment++;
                    } else {
                        code++;
                    }
                } else {
                    code++;
                }
            }
            info.setLines(total);
            info.setCodeLines(code);
            info.setCommentLines(comment);
            info.setBlankLines(blank);
        } catch (IOException e) {
            System.err.println("Error reading " + file + ": " + e.getMessage());
        }
    }
}