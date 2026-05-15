package com.codestats.framework.log.layout;


import com.codestats.framework.log.core.LogEvent;

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
        while (m.find()) {
            String formatFlag = m.group(1);   // 如 "-5"
            String keyword = m.group(2);      // level, logger, d, t, msg, m, n, ex
            String option = m.group(3);       // 如 "yyyy-MM-dd HH:mm:ss.SSS" 或 "36"
            String replacement = convert(event, keyword, option, formatFlag);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
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
                String loggerName = event.getLoggerName();
                if (option != null) {
                    int maxLen = Integer.parseInt(option);
                    if (loggerName.length() > maxLen) {
                        // 保留最后 maxLen 个字符，前面加 "..."
                        loggerName = "..." + loggerName.substring(loggerName.length() - maxLen + 3);
                    }
                }
                return loggerName;
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
                StringBuilder sb = new StringBuilder();
                sb.append(t.toString()).append("\n");
                for (StackTraceElement ste : t.getStackTrace()) {
                    sb.append("\tat ").append(ste).append("\n");
                }
                return sb.toString();
            default:
                return "%" + keyword;
        }
    }
}