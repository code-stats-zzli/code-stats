package com.omni.framework.datacube.model;

import com.omni.framework.jdbc.metedata.model.ColumnInfo;

/** 列元数据（前端/组件统一消费） */
public class ColumnMeta {
    private String name;
    private String label;
    private String type;          // string | int | decimal | date | boolean | json
    private Integer size;
    private boolean nullable;
    private boolean autoIncrement;
    private String remarks;

    public static ColumnMeta from(ColumnInfo c) {
        ColumnMeta m = new ColumnMeta();
        m.name = c.getColumnName();
        m.label = c.getRemarks() != null && !c.getRemarks().isEmpty()
                ? c.getRemarks() : c.getColumnName();
        m.type = toSimpleType(c.getTypeName());
        m.size = c.getColumnSize();
        m.nullable = c.isNullable();
        m.autoIncrement = c.isAutoIncrement();
        m.remarks = c.getRemarks();
        return m;
    }

    private static String toSimpleType(String sqlType) {
        if (sqlType == null) return "string";
        String t = sqlType.toUpperCase();
        if (t.contains("INT"))     return "int";
        if (t.contains("DECIMAL") || t.contains("NUMERIC")
                || t.contains("FLOAT") || t.contains("DOUBLE")) return "decimal";
        if (t.contains("DATE") || t.contains("TIME"))          return "date";
        if (t.contains("BOOL") || t.contains("BIT"))           return "boolean";
        if (t.contains("JSON"))                                 return "json";
        return "string";
    }

    // getters / setters
    public String getName()  { return name; }
    public void   setName(String name) { this.name = name; }
    public String getLabel() { return label; }
    public void   setLabel(String label) { this.label = label; }
    public String getType()  { return type; }
    public void   setType(String type) { this.type = type; }
    public Integer getSize() { return size; }
    public void   setSize(Integer size) { this.size = size; }
    public boolean isNullable() { return nullable; }
    public void   setNullable(boolean nullable) { this.nullable = nullable; }
    public boolean isAutoIncrement() { return autoIncrement; }
    public void   setAutoIncrement(boolean autoIncrement) { this.autoIncrement = autoIncrement; }
    public String getRemarks() { return remarks; }
    public void   setRemarks(String remarks) { this.remarks = remarks; }
}