package com.framework.jdbc.metedata.datamodel;

import java.util.*;

public class DataModel implements IDataModel {
    private final List<ColumnDef> columnDefs;
    private final List<Row> rows;
    private final Map<String, Integer> nameIndexMap;   // 列名 -> 索引
    private String description = "";

    public DataModel(List<ColumnDef> columnDefs, List<Row> rows) {
        this.columnDefs = Collections.unmodifiableList(new ArrayList<>(columnDefs));
        this.rows = new ArrayList<>(rows);
        this.nameIndexMap = new HashMap<>();
        for (int i = 0; i < columnDefs.size(); i++) {
            nameIndexMap.put(columnDefs.get(i).getName(), i);
        }
    }

    @Override
    public List<ColumnDef> getColumnDefs() {
        return columnDefs;
    }

    @Override
    public int getColumnCount() {
        return columnDefs.size();
    }

    @Override
    public String[] getColumnNames() {
        return columnDefs.stream().map(ColumnDef::getName).toArray(String[]::new);
    }

    @Override
    public String getColumnName(int index) {
        return columnDefs.get(index).getName();
    }

    @Override
    public int getColumnIndex(String name) {
        return nameIndexMap.getOrDefault(name, -1);
    }

    @Override
    public ColumnDef getColumnDef(int index) {
        return columnDefs.get(index);
    }

    @Override
    public ColumnDef getColumnDef(String name) {
        int idx = getColumnIndex(name);
        return idx == -1 ? null : columnDefs.get(idx);
    }

    @Override
    public List<Row> getRows() {
        return Collections.unmodifiableList(rows);
    }

    @Override
    public Row getRow(int index) {
        return rows.get(index);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public Object getValue(int rowIndex, int colIndex) {
        return rows.get(rowIndex).getValue(colIndex);
    }

    @Override
    public Object getValue(int rowIndex, String columnName) {
        return rows.get(rowIndex).getValue(columnName);
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String desc) {
        this.description = desc;
    }
}