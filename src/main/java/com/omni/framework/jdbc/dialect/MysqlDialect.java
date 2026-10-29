package com.omni.framework.jdbc.dialect;

import com.omni.framework.spring.annotation.Component;

@Component
public class MysqlDialect implements SqlDialect {
    @Override public String getName() { return "mysql"; }

    @Override
    public String buildPageSql(String baseSql, int offset, int pageSize) {
        return baseSql + " LIMIT " + offset + "," + pageSize;
    }

    @Override
    public String quote(String identifier) {
        return "`" + identifier + "`";
    }
}