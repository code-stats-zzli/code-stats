package com.omni.common.expression;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 表达式文本处理工具（纯 JDK，无框架/业务依赖）。
 * 负责：
 *   1. 剥离 $ 变量前缀（跳过字符串/字符字面量）
 *   2. 变量表键名 $ 前缀归一化
 *   3. 变量名合法性校验
 */
public final class ExpressionTextUtils {

    private ExpressionTextUtils() {}

    /**
     * 将表达式中的 $ 变量前缀剥离。
     *   "1 + $a * 2"     → "1 + a * 2"
     *   "\"$a\" + $b"     → "\"$a\" + b"（字符串内部不处理）
     *   仅当 $ 后紧跟 [A-Za-z_] 才视为变量，$1、$$ 不动。
     */
    public static String stripDollarPrefix(String expression) {
        if (expression == null || expression.isEmpty()) return expression;
        StringBuilder sb = new StringBuilder(expression.length());
        int i = 0;
        char quote = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (quote != 0) {
                sb.append(c);
                if (c == '\\' && i + 1 < expression.length()) {
                    sb.append(expression.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == quote) quote = 0;
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                sb.append(c);
                i++;
                continue;
            }
            if (c == '$') {
                int j = i + 1;
                if (j < expression.length()
                        && (Character.isLetter(expression.charAt(j)) || expression.charAt(j) == '_')) {
                    j++;
                    while (j < expression.length()
                            && (Character.isLetterOrDigit(expression.charAt(j)) || expression.charAt(j) == '_')) {
                        j++;
                    }
                    sb.append(expression, i + 1, j);   // 跳过 $
                    i = j;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * 变量表键去 $ 前缀。{"$name":"Tom","$age":18} → {"name":"Tom","age":18}
     */
    public static Map<String, Object> normalizeDollarVariables(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = normalizeVariableName(e.getKey());
            if (key != null && !key.isEmpty()) out.put(key, e.getValue());
        }
        return out;
    }

    /**
     * 归一化单个变量名：去空白、去 $ 前缀。
     * 无效名返回 null。
     */
    public static String normalizeVariableName(String name) {
        if (name == null) return null;
        String s = name.trim();
        if (s.startsWith("$")) s = s.substring(1);
        return s;
    }

    /**
     * 校验变量名是否符合普通标识符规则：^[A-Za-z_]\w*$
     */
    public static boolean isValidVariableName(String name) {
        if (name == null || name.isEmpty()) return false;
        char c0 = name.charAt(0);
        if (!(Character.isLetter(c0) || c0 == '_')) return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }
}