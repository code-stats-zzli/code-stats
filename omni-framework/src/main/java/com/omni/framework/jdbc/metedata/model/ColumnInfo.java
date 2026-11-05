package com.omni.framework.jdbc.metedata.model;

/**
 * 列详细信息。
 */
public class ColumnInfo {
    private final String columnName;        // 列名
    private final int dataType;             // SQL 类型（java.sql.Types）
    private final String typeName;          // 数据库类型名称，如 "INT"
    private final int columnSize;           // 列大小
    private final int decimalDigits;        // 小数位数
    private final boolean nullable;         // 是否可为空
    private final String remarks;           // 备注说明
    private final String defaultValue;      // 默认值
    private final int ordinalPosition;      // 列序号（从1开始）
    private final boolean autoIncrement;    // 是否自增列
    private final boolean generated;        // 是否生成的列

    public ColumnInfo(String columnName, int dataType, String typeName, int columnSize,
                      int decimalDigits, boolean nullable, String remarks, String defaultValue,
                      int ordinalPosition, boolean autoIncrement, boolean generated) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.typeName = typeName;
        this.columnSize = columnSize;
        this.decimalDigits = decimalDigits;
        this.nullable = nullable;
        this.remarks = remarks;
        this.defaultValue = defaultValue;
        this.ordinalPosition = ordinalPosition;
        this.autoIncrement = autoIncrement;
        this.generated = generated;
    }

    public String getColumnName() { return columnName; }
    public int getDataType() { return dataType; }
    public String getTypeName() { return typeName; }
    public int getColumnSize() { return columnSize; }
    public int getDecimalDigits() { return decimalDigits; }
    public boolean isNullable() { return nullable; }
    public String getRemarks() { return remarks; }
    public String getDefaultValue() { return defaultValue; }
    public int getOrdinalPosition() { return ordinalPosition; }
    public boolean isAutoIncrement() { return autoIncrement; }
    public boolean isGenerated() { return generated; }

    @Override
    public String toString() {
        return "Column{'" + columnName + "', type='" + typeName + "(" + columnSize + ")" +
                "', nullable=" + nullable + ", remark='" + remarks + "'}";
    }
}