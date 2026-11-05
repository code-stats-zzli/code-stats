package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 支持列名映射的解析器（如：数据库列名 -> 显示名）。
 * 优先级：1（最低），别名解析应在所有真实数据源之后。
 */
public class ColumnMappingResolver implements VariableResolver {

    private final Map<String, String> columnMapping;

    // 线程本地递归检测（防循环映射）
    private static final ThreadLocal<Set<String>> RESOLVING = ThreadLocal.withInitial(HashSet::new);

    public ColumnMappingResolver(Map<String, String> columnMapping) {
        this.columnMapping = columnMapping;
    }

    @Override
    public Object resolve(String name, EvaluationContext context) {
        if (!columnMapping.containsKey(name)) {
            return null;
        }

        String actualName = columnMapping.get(name);

        // 检测循环引用
        if (RESOLVING.get().contains(actualName)) {
            return null;
        }

        try {
            RESOLVING.get().add(actualName);
            return context.getVariable(actualName);
        } finally {
            RESOLVING.get().remove(actualName);
        }
    }

    @Override
    public int getPriority() {
        return 1; // 最低优先级
    }
}