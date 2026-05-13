package com.codestats.lib.codestats.pipeline;

import com.codestats.lib.codestats.core.FileInfo;
import com.codestats.lib.codestats.language.Language;
import com.codestats.lib.codestats.language.LanguageDetector;
import com.codestats.lib.codestats.language.LanguageSpec;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeStage implements Stage<Path, FileInfo> {
    private final LanguageDetector detector = new LanguageDetector();
    private final boolean excludeComments;
    private static final Pattern COMPLEXITY_KEYWORDS = Pattern.compile(
            "\\b(if|else|for|while|do|switch|case|catch|\\?\\?)\\b");

    public AnalyzeStage(boolean excludeComments) {
        this.excludeComments = excludeComments;
    }

    @Override
    public FileInfo process(Path file) {
        FileInfo info = new FileInfo(file);
        Language lang = detector.detect(file);
        info.setLanguage(lang);
        LanguageSpec spec = LanguageSpec.forLanguage(lang);
        int complexity = 0;

        if (spec.getLineComment() == null && spec.getBlockStart() == null) {
            // 无法识别，只统计总行数
            long total = countLines(file);
            info.setLines(total);
            if (excludeComments) info.setCodeLines(total);
            return info;
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            long total = 0, code = 0, comment = 0, blank = 0;
            boolean inBlock = false;
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    blank++;
                    continue;
                }
                // 复杂度统计（仅对code行）
                if (!inBlock && !trimmed.startsWith(spec.getLineComment() != null ? spec.getLineComment() : "")) {
                    // 统计 line 中匹配 COMPLEXITY_KEYWORDS 的次数（Java 8 兼容）
                    Matcher m = COMPLEXITY_KEYWORDS.matcher(line);
                    int count = 0;
                    while (m.find()) {
                        count++;
                    }
                    complexity += count;
                }

                if (inBlock) {
                    comment++;
                    if (spec.getBlockEnd() != null && trimmed.contains(spec.getBlockEnd())) {
                        inBlock = false;
                    }
                    continue;
                }
                if (spec.getBlockStart() != null && trimmed.startsWith(spec.getBlockStart())) {
                    comment++;
                    if (spec.getBlockEnd() != null && !trimmed.contains(spec.getBlockEnd())) {
                        inBlock = true;
                    }
                    continue;
                }
                if (spec.getLineComment() != null && trimmed.startsWith(spec.getLineComment())) {
                    comment++;
                    continue;
                }
                code++;
            }
            info.setLines(total);
            info.setCodeLines(code);
            info.setCommentLines(comment);
            info.setBlankLines(blank);
            info.setComplexity(complexity);
        } catch (Exception e) {
            System.err.println("Error reading " + file + ": " + e.getMessage());
        }
        return info;
    }

    private long countLines(Path file) {
        try (BufferedReader r = Files.newBufferedReader(file)) {
            return r.lines().count();
        } catch (Exception e) {
            return 0;
        }
    }
}