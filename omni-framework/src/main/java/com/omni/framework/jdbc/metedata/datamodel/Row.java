package com.omni.framework.jdbc.metedata.datamodel;

/**
 * 数据行，提供按索引或列名获取值的能力。
 */
public interface Row {
    Object getValue(int columnIndex);
    Object getValue(String columnName);
    int getColumnCount();
    String[] getColumnNames();          // 从关联的 DataModel 获取
    IDataModel getModel();               // 获取所属的 DataModel
}