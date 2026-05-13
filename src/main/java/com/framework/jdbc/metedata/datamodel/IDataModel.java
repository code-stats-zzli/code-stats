package com.framework.jdbc.metedata.datamodel;

import java.util.List;

/**
 * 通用数据模型，封装列定义和数据行。
 * 支持通过索引或列名访问任意单元格，并提供行/列的迭代能力。
 */
public interface IDataModel {
    // --- 列相关 ---
    List<ColumnDef> getColumnDefs();
    int getColumnCount();
    String[] getColumnNames();              // 返回所有列名的数组
    String getColumnName(int index);
    int getColumnIndex(String name);        // 不存在返回 -1
    ColumnDef getColumnDef(int index);
    ColumnDef getColumnDef(String name);

    // --- 行相关 ---
    List<Row> getRows();
    Row getRow(int index);
    int getRowCount();
    boolean isEmpty();

    // --- 单元格访问 ---
    Object getValue(int rowIndex, int colIndex);
    Object getValue(int rowIndex, String columnName);

    // --- 整体信息 ---
    String getDescription();                // 可选，数据集描述
    void setDescription(String desc);
}