package com.omni.framework.expression.resolver.impl;
import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

import java.util.Map;

/**
 * 支持列名映射的解析器（如：数据库列名 -> 显示名）。
 */
public class ColumnMappingResolver implements VariableResolver {

    private final Map<String, String> columnMapping;  // 表达式名 -> 实际列名

    public ColumnMappingResolver(Map<String, String> columnMapping) {
        this.columnMapping = columnMapping;
    }

    @Override
    public Object resolve(String name, EvaluationContext context) {
        if (columnMapping == null || !columnMapping.containsKey(name)) {
            return null;
        }
        // 拿到映射后的实际列名，然后从上下文中查找
        String actualName = columnMapping.get(name);
        // 尝试从根对象、Map 等获取，复用上下文自身的查找逻辑
        return context.getVariable(actualName);
    }
}
