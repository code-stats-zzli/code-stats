package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

import java.util.Map;

/**
 * Map 数据源解析器（直接键值映射）。
 * 优先级：2
 */
public class RowMappingResolver implements VariableResolver {

    private final Map<String, Object> rowMapping;

    public RowMappingResolver(Map<String, Object> rowMapping) {
        this.rowMapping = rowMapping;
    }

    @Override
    public Object resolve(String name, EvaluationContext context) {
        if (rowMapping == null || !rowMapping.containsKey(name)) {
            return null;
        }
        return rowMapping.get(name);
    }

    @Override
    public int getPriority() {
        return 2;
    }
}