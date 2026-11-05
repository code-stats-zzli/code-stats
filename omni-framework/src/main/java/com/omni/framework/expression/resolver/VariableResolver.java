package com.omni.framework.expression.resolver;

import com.omni.framework.expression.EvaluationContext;

/**
 * 变量解析器接口。
 * <p>
 * 支持优先级控制：数值越大优先级越高，同优先级按添加顺序执行。
 * 默认优先级为 1（最低）。
 */
@FunctionalInterface
public interface VariableResolver {

    /**
     * 根据变量名解析值。
     *
     * @param name    变量名（表达式中的标识符）
     * @param context 当前求值上下文
     * @return 解析到的值，如果无法解析则返回 {@code null}
     */
    Object resolve(String name, EvaluationContext context);

    /**
     * 返回优先级，数值越大优先级越高。
     * 默认返回 1（最低优先级）。
     */
    default int getPriority() {
        return 1;
    }

    /**
     * 组合多个解析器，按优先级排序后执行。
     *
     * @param resolvers 解析器列表
     * @return 组合后的解析器
     */
    static VariableResolver chain(VariableResolver... resolvers) {
        return (name, context) -> {
            for (VariableResolver resolver : resolvers) {
                Object value = resolver.resolve(name, context);
                if (value != null) {
                    return value;
                }
            }
            return null;
        };
    }
}