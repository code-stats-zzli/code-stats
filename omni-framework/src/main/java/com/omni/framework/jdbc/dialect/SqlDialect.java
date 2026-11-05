package com.omni.framework.jdbc.dialect;

/**
 * SQL 方言策略接口。新增数据库类型只需实现本接口并加 {@code @Component}。
 */
public interface SqlDialect {

    /** 方言名称（对应 DataSourceUtils.DbType 或配置的 dbType 小写），如 "mysql" / "oracle" */
    String getName();

    /** 构建分页 SQL */
    String buildPageSql(String baseSql, int offset, int pageSize);

    /** 标识符引用（表名、列名） */
    String quote(String identifier);
}