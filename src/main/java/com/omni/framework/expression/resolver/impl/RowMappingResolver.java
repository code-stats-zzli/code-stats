package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

import java.util.Map;

public class RowMappingResolver implements VariableResolver {

    private final Map<String, Object> rowMapping;  // 表达式名 -> 实际列名

    public RowMappingResolver(Map<String, Object> rowMapping) {
        this.rowMapping = rowMapping;
    }

    @Override
    public Object resolve(String name, EvaluationContext context) {
        if (rowMapping == null || !rowMapping.containsKey(name)) {
            return null;
        }
        // 拿到映射后的实际列名，然后从上下文中查找
        return rowMapping.get(name);
    }
}