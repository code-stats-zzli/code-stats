package com.omni.framework.log.layout;

import com.omni.framework.log.core.LogEvent;
import com.omni.framework.util.ExceptionUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternLayout extends Layout {
    private final String pattern;
    // 匹配占位符：%[-整数]关键字[{参数}]  例如 %-5level, %d{yyyy-MM-dd}, %logger{36}
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("%(-?\\d+)?(level|logger|d|t|msg|m|n|ex)(?:\\{([^}]+)\\})?");

    public PatternLayout(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String format(LogEvent event) {
        StringBuffer sb = new StringBuffer();
        Matcher m = PLACEHOLDER_PATTERN.matcher(pattern);
        boolean hasExplicitEx = false;
        while (m.find()) {
            String formatFlag = m.group(1);   // 如 "-5"
            String keyword = m.group(2);      // level, logger, d, t, msg, m, n, ex
            String option = m.group(3);       // 如 "yyyy-MM-dd HH:mm:ss.SSS" 或 "36"
            String replacement = convert(event, keyword, option, formatFlag);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            if ("ex".equals(keyword)) {
                hasExplicitEx = true;
            }
        }
        m.appendTail(sb);

        // 如果存在异常且 pattern 中没有显式 %ex，则自动追加完整堆栈（包含 cause 链）
        Throwable t = event.getThrowable();
        if (t != null && !hasExplicitEx) {
            sb.append(System.lineSeparator());
            sb.append(ExceptionUtil.getFullStackTrace(t));
        }

        return sb.toString();
    }

    private String convert(LogEvent event, String keyword, String option, String formatFlag) {
        switch (keyword) {
            case "level":
                String levelName = event.getLevel().name();
                if (formatFlag != null && formatFlag.startsWith("-")) {
                    int width = Integer.parseInt(formatFlag.substring(1));
                    return String.format("%-" + width + "s", levelName);
                }
                return levelName;

            case "logger":
                // ★ 修复2：始终输出完整类全限定名，不截断
                // 即使用户配置了 %logger{36} 也完整输出，避免调试时定位不到类
                // 若后续希望按需截断，请用其他保留关键字（如 %shortLogger），这里不再支持
                return event.getLoggerName();

            case "d":
                DateTimeFormatter formatter;
                if (option == null || option.isEmpty()) {
                    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                } else {
                    formatter = DateTimeFormatter.ofPattern(option);
                }
                LocalDateTime dt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(event.getTimestamp()), ZoneId.systemDefault());
                return dt.format(formatter);

            case "t":
                return event.getThreadName();

            case "msg":
            case "m":
                return event.getMessage();

            case "n":
                return System.lineSeparator();

            case "ex":
                Throwable t = event.getThrowable();
                if (t == null) return "";
                return ExceptionUtil.getFullStackTrace(t);

            default:
                return "%" + keyword;
        }
    }
}