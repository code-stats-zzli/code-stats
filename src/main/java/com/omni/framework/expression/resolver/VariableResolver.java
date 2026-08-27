package com.omni.framework.expression.resolver;

import com.omni.framework.expression.EvaluationContext;

/**
 * 变量解析器接口。
 * <p>
 * 用于自定义“如何根据变量名获取值”，支持链式组合。
 * 典型的实现：
 * <ul>
 *   <li>MapVariableResolver：从 Map 中取数</li>
 *   <li>ColumnMappingResolver：列名映射（如 databaseColumn -> displayName）</li>
 *   <li>LazyLoadResolver：首次访问时从数据库/RPC 加载</li>
 * </ul>
 */
@FunctionalInterface
public interface VariableResolver {

    /**
     * 根据变量名解析值。
     *
     * @param name    变量名（表达式中的标识符）
     * @param context 当前求值上下文（可获取根对象、其他变量等）
     * @return 解析到的值，如果无法解析则返回 {@code null}（后续解析器会继续尝试）
     */
    Object resolve(String name, EvaluationContext context);

    /**
     * 组合多个解析器，按顺序执行，第一个返回非 null 的值作为结果。
     *
     * @param resolvers 解析器列表（按优先级顺序）
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