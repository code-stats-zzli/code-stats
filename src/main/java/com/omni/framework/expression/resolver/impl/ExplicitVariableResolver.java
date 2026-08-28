package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;

/**
 * 从显式注册的变量 Map 中获取值。
 */
public class ExplicitVariableResolver implements VariableResolver {

    private final EvaluationContext context;

    public ExplicitVariableResolver(EvaluationContext context) {
        this.context = context;
    }

    @Override
    public Object resolve(String name, EvaluationContext context) {
        // 优先取显式注册的变量（包括 null 值）
        if (context.getVariables().containsKey(name)) {
            return context.getVariable(name);
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 10; // 内置优先级：10
    }
}