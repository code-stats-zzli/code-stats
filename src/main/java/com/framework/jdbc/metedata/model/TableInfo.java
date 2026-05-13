package com.framework.jdbc.metedata.model;

/**
 * 表摘要信息。
 */
public class TableInfo {
    private final String catalog;      // 数据库名
    private final String schema;       // 模式名（MySQL 中通常为 null）
    private final String name;         // 表名
    private final String type;         // 表类型：TABLE, VIEW, etc.
    private final String remarks;      // 备注说明

    public TableInfo(String catalog, String schema, String name, String type, String remarks) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = name;
        this.type = type;
        this.remarks = remarks;
    }

    public String getCatalog() { return catalog; }
    public String getSchema() { return schema; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getRemarks() { return remarks; }

    @Override
    public String toString() {
        return "Table{name='" + name + "', type='" + type + "', remarks='" + remarks + "'}";
    }
}