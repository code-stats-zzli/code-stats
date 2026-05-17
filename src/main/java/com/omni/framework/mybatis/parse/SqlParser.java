// ======== com/omni/framework/mybatis/util/SqlParser.java ========
package com.omni.framework.mybatis.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlParser {
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{(\\w+)\\}");

    /**
     * 将 SQL 中的 #{name} 替换为 ? ，并返回参数名列表（按出现顺序）
     */
    public static ParsedSql parse(String sql) {
        List<String> paramNames = new ArrayList<>();
        StringBuffer sb = new StringBuffer();
        Matcher m = PLACEHOLDER.matcher(sql);
        while (m.find()) {
            String name = m.group(1);
            paramNames.add(name);
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        return new ParsedSql(sb.toString(), paramNames);
    }

    public static class ParsedSql {
        private final String sql;
        private final List<String> paramNames;
        public ParsedSql(String sql, List<String> paramNames) {
            this.sql = sql;
            this.paramNames = paramNames;
        }
        public String getSql() { return sql; }
        public List<String> getParamNames() { return paramNames; }
    }
}