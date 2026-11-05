package com.omni.framework.expression;

import com.omni.framework.expression.resolver.VariableResolver;
import com.omni.framework.expression.resolver.impl.ExplicitVariableResolver;
import com.omni.framework.expression.resolver.impl.RootObjectResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 支持自定义变量解析器链的评估上下文。
 * <p>
 * 解析器按优先级降序执行（数值越大越先执行）。
 * 同优先级的解析器保持添加顺序。
 * 显式变量（通过 setVariable 注册）始终优先于所有解析器。
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
        // 显式变量由父类 getVariable 直接处理，不放入 resolvers 列表
        if (rootObject != null) {
            addResolver(new RootObjectResolver(rootObject)); // RootObjectResolver 内部优先级为 4
        }
        if (customResolvers != null) {
            customResolvers.forEach(this::addResolver);
        }
        // 添加后自动排序
    }

    // ==================== 解析器管理（带排序） ====================


    public VariableAwareEvaluationContext addResolver(VariableResolver resolver) {
        if (resolver != null) {
            resolvers.add(resolver);
            sortResolvers();
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

    /**
     * 对解析器列表进行排序：降序（优先级数值大的在前）
     * 同优先级保持原有顺序（稳定排序）
     */
    private void sortResolvers() {
        Collections.sort(resolvers, new Comparator<VariableResolver>() {
            @Override
            public int compare(VariableResolver a, VariableResolver b) {
                // 降序：b.getPriority() - a.getPriority()
                return Integer.compare(b.getPriority(), a.getPriority());
            }
        });
        // Collections.sort 是稳定排序，同优先级保持原顺序
    }

    // ==================== 核心变量获取逻辑 ====================

    @Override
    public Object getVariable(String name) {
        // 1. 先查显式注册的变量（通过父类的 Map）——优先级最高
        if (super.getVariables().containsKey(name)) {
            return super.getVariable(name);
        }

        // 2. 遍历解析器链（已按优先级降序排序），第一个返回非 null 的值
        for (VariableResolver resolver : resolvers) {
            Object value = resolver.resolve(name, this);
            if (value != null) {
                return value;
            }
        }

        // 3. 全部未找到，返回 null
        return null;
    }

    @Override
    public void setVariable(String name, Object value) {
        super.setVariable(name, value);
    }
}