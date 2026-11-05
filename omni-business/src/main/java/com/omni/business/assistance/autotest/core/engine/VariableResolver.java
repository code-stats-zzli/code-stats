package com.omni.business.assistance.autotest.core.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public static String resolve(String text, Map<String, Object> variables) {
        if (text == null) return null;
        Matcher m = VAR_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String varName = m.group(1);
            Object value = variables.get(varName);
            String replacement = value != null ? value.toString() : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}