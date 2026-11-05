package com.omni.framework.jdbc.metedata.datamodel;

/**
 * 列定义，描述数据模型中的一列。
 */
public class ColumnDef {
    private final String name;          // 列名
    private final String typeName;      // 数据类型名称，如 "INT", "VARCHAR"
    private final int sqlType;          // java.sql.Types 值
    private final boolean nullable;     // 是否可为空
    private final String remarks;       // 备注
    private final int columnSize;       // 列大小
    private final int decimalDigits;    // 小数位

    public ColumnDef(String name, String typeName, int sqlType, boolean nullable,
                     String remarks, int columnSize, int decimalDigits) {
        this.name = name;
        this.typeName = typeName;
        this.sqlType = sqlType;
        this.nullable = nullable;
        this.remarks = remarks;
        this.columnSize = columnSize;
        this.decimalDigits = decimalDigits;
    }

    public String getName() { return name; }
    public String getTypeName() { return typeName; }
    public int getSqlType() { return sqlType; }
    public boolean isNullable() { return nullable; }
    public String getRemarks() { return remarks; }
    public int getColumnSize() { return columnSize; }
    public int getDecimalDigits() { return decimalDigits; }

    @Override
    public String toString() {
        return name + " " + typeName + (nullable ? " NULL" : " NOT NULL");
    }
}