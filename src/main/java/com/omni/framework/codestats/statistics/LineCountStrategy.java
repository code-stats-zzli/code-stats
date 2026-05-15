package com.omni.framework.codestats.statistics;

import com.omni.framework.codestats.core.FileInfo;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LineCountStrategy implements StatisticStrategy {
    private static final Logger log = LoggerFactory.getLogger(LineCountStrategy.class);
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
            log.error("Error reading file: {}", file, e);
        }
    }
}