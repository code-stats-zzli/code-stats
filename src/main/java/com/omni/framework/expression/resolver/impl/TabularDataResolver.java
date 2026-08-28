package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单行表格数据解析器。
 * 优先级：3
 */
public class TabularDataResolver implements VariableResolver {
    private final Map<String, Integer> columnIndexMap = new HashMap<>();
    private final Object[] rowData;

    public TabularDataResolver(List<String> headers, Object[] rowData) {
        for (int i = 0; i < headers.size(); i++) {
            columnIndexMap.put(headers.get(i), i);
        }
        this.rowData = rowData;
    }

    @Override
    public Object resolve(String name, EvaluationContext context) {
        Integer idx = columnIndexMap.get(name);
        if (idx != null && idx >= 0 && idx < rowData.length) {
            return rowData[idx];
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 3;
    }
}