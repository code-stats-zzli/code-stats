package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可切换行号的多行表格数据解析器。
 * 优先级：3
 */
public class SwitchableRowsResolver implements VariableResolver {

    private final Map<String, Integer> columnIndexMap = new HashMap<>();
    private final List<Object[]> allRows;
    private int currentRowIndex = -1;
    private Object[] currentRowData = null;

    public SwitchableRowsResolver(List<String> headers, List<Object[]> rows) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("headers cannot be null or empty");
        }
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null) {
                columnIndexMap.put(header, i);
            }
        }
        this.allRows = (rows != null) ? rows : List.of();
        if (!this.allRows.isEmpty()) {
            setRowIndex(0);
        }
    }

    // ==================== 行号切换方法（保持不变） ====================

    public SwitchableRowsResolver setRowIndex(int index) {
        if (index < 0 || index >= allRows.size()) {
            throw new IndexOutOfBoundsException(
                    "Row index " + index + " out of bounds, total rows: " + allRows.size()
            );
        }
        this.currentRowIndex = index;
        this.currentRowData = allRows.get(index);
        return this;
    }

    public boolean nextRow() {
        if (currentRowIndex < allRows.size() - 1) {
            setRowIndex(currentRowIndex + 1);
            return true;
        }
        return false;
    }

    public boolean previousRow() {
        if (currentRowIndex > 0) {
            setRowIndex(currentRowIndex - 1);
            return true;
        }
        return false;
    }

    public SwitchableRowsResolver resetToFirst() {
        if (!allRows.isEmpty()) {
            setRowIndex(0);
        }
        return this;
    }

    public SwitchableRowsResolver clearRow() {
        this.currentRowIndex = -1;
        this.currentRowData = null;
        return this;
    }

    public int getCurrentRowIndex() {
        return currentRowIndex;
    }

    public int getRowCount() {
        return allRows.size();
    }

    public Object[] getCurrentRowData() {
        return currentRowData != null ? currentRowData.clone() : null;
    }

    public Object getValue(String columnName) {
        if (currentRowData == null) {
            return null;
        }
        Integer index = columnIndexMap.get(columnName);
        if (index == null || index < 0 || index >= currentRowData.length) {
            return null;
        }
        return currentRowData[index];
    }

    public List<Object[]> getAllRows() {
        return allRows;
    }

    public Map<String, Integer> getColumnIndexMap() {
        return new HashMap<>(columnIndexMap);
    }

    // ==================== VariableResolver 接口实现 ====================

    @Override
    public Object resolve(String name, EvaluationContext context) {
        if (currentRowData == null) {
            return null;
        }
        Integer index = columnIndexMap.get(name);
        if (index == null || index < 0 || index >= currentRowData.length) {
            return null;
        }
        return currentRowData[index];
    }

    @Override
    public int getPriority() {
        return 3;
    }
}