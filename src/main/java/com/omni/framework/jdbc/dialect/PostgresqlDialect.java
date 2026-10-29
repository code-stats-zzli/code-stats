package com.omni.framework.jdbc.dialect;

import com.omni.framework.spring.annotation.Component;

@Component
public class PostgresqlDialect implements SqlDialect {
    @Override public String getName() { return "postgresql"; }

    @Override
    public String buildPageSql(String baseSql, int offset, int pageSize) {
        return baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
    }

    @Override
    public String quote(String identifier) {
        return "\"" + identifier + "\"";
    }
}