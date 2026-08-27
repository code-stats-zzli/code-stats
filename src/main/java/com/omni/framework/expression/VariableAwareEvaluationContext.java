package com.omni.framework.expression;

import com.omni.framework.expression.resolver.VariableResolver;
import com.omni.framework.expression.resolver.impl.ExplicitVariableResolver;
import com.omni.framework.expression.resolver.impl.RootObjectResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持自定义变量解析器链的评估上下文。
 * <p>
 * 使用场景：
 * <ul>
 *   <li>表格列与数据分离：RowDataResolver + ColumnMappingResolver</li>
 *   <li>多级数据源：先查本地 Map，再查根对象，最后查数据库</li>
 * </ul>
 */
public class VariableAwareEvaluationContext extends StandardEvaluationContext {

    private final List<VariableResolver> resolvers = new ArrayList<>();

    // ==================== 构造方法 ====================

    public VariableAwareEvaluationContext() {
        this(null, null);
    }

    public VariableAwareEvaluationContext(Object rootObject) {
        this(rootObject, null);
    }

    public VariableAwareEvaluationContext(Object rootObject, List<VariableResolver> customResolvers) {
        // 默认解析器链：先查显式变量，再查根对象
        addResolver(new ExplicitVariableResolver(this));
        if (rootObject != null) {
            addResolver(new RootObjectResolver(rootObject));
        }
        if (customResolvers != null) {
            customResolvers.forEach(this::addResolver);
        }
    }

    // ==================== 解析器管理 ====================

    public VariableAwareEvaluationContext addResolver(VariableResolver resolver) {
        if (resolver != null) {
            resolvers.add(resolver);
        }
        return this;
    }

    public VariableAwareEvaluationContext addResolvers(List<VariableResolver> resolvers) {
        if (resolvers != null) {
            resolvers.forEach(this::addResolver);
        }
        return this;
    }

    public List<VariableResolver> getResolvers() {
        return resolvers;
    }

    // ==================== 核心变量获取逻辑 ====================

    @Override
    public Object getVariable(String name) {
        // 1. 先查显式注册的变量（通过父类的 Map）
        if (super.getVariables().containsKey(name)) {
            return super.getVariable(name);
        }

        // 2. 遍历解析器链，第一个返回非 null 的值
        for (VariableResolver resolver : resolvers) {
            Object value = resolver.resolve(name, this);
            if (value != null) {
                return value;
            }
        }

        // 3. 全部未找到，返回 null（表达式可自行判空）
        return null;
    }

    @Override
    public void setVariable(String name, Object value) {
        super.setVariable(name, value);
    }
}