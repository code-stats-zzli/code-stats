package com.omni.framework.jdbc.metedata.datamodel;

import java.util.List;

public class SimpleRow implements Row {
    private final IDataModel model;
    private final List<Object> values;      // 按列顺序存储的值

    public SimpleRow(IDataModel model, List<Object> values) {
        this.model = model;
        this.values = values;
    }

    @Override
    public Object getValue(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= values.size()) {
            throw new IndexOutOfBoundsException("Column index out of range: " + columnIndex);
        }
        return values.get(columnIndex);
    }

    @Override
    public Object getValue(String columnName) {
        int idx = model.getColumnIndex(columnName);
        if (idx == -1) {
            throw new IllegalArgumentException("Column not found: " + columnName);
        }
        return values.get(idx);
    }

    @Override
    public int getColumnCount() {
        return values.size();
    }

    @Override
    public String[] getColumnNames() {
        return model.getColumnNames();
    }

    @Override
    public IDataModel getModel() {
        return model;
    }
}