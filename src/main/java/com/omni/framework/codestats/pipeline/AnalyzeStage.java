package com.omni.framework.codestats.pipeline;

import com.omni.framework.codestats.core.FileInfo;
import com.omni.framework.codestats.language.Language;
import com.omni.framework.codestats.language.LanguageDetector;
import com.omni.framework.codestats.language.LanguageSpec;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeStage implements Stage<Path, FileInfo> {
    private static final Logger log = LoggerFactory.getLogger(AnalyzeStage.class);

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

        // 无注释规则语言（HTML/XML 等）：分项与总行数保持一致
        if (spec.getLineComment() == null && spec.getBlockStart() == null) {
            try (BufferedReader r = Files.newBufferedReader(file)) {
                long total = 0, blank = 0;
                String line;
                while ((line = r.readLine()) != null) {
                    total++;
                    if (line.trim().isEmpty()) blank++;
                    Matcher m = COMPLEXITY_KEYWORDS.matcher(line);
                    while (m.find()) complexity++;
                }
                info.setLines(total);
                info.setCodeLines(total - blank);
                info.setCommentLines(0);
                info.setBlankLines(blank);
                info.setComplexity(complexity);
            } catch (Exception e) {
                log.error("读取文件失败: {}", file, e);
            }
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

                // ★ [P0] 复杂度统计只对非注释行生效
                boolean isCommentLine = inBlock
                        || (spec.getLineComment() != null && trimmed.startsWith(spec.getLineComment()))
                        || (spec.getBlockStart() != null && trimmed.startsWith(spec.getBlockStart()));
                if (!isCommentLine) {
                    Matcher m = COMPLEXITY_KEYWORDS.matcher(line);
                    while (m.find()) complexity++;
                }

                if (inBlock) {
                    comment++;
                    if (spec.getBlockEnd() != null && trimmed.contains(spec.getBlockEnd())) inBlock = false;
                    continue;
                }
                if (spec.getBlockStart() != null && trimmed.startsWith(spec.getBlockStart())) {
                    comment++;
                    if (spec.getBlockEnd() != null && !trimmed.contains(spec.getBlockEnd())) inBlock = true;
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
            log.error("读取文件失败: {}", file, e);
        }
        return info;
    }
}