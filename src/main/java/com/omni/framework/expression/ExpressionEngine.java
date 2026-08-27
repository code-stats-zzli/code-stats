package com.omni.framework.expression;

import com.omni.framework.expression.function.BuiltinFunctions;
import com.omni.framework.expression.function.FunctionResolver;

import java.util.Map;

/**
 * 标准表达式引擎实现类。
 * <p>
 * 特性：
 * <ul>
 *   <li>默认使用 {@link StandardEvaluationContext} 作为运行时上下文</li>
 *   <li>默认使用 {@link StandardExpressionParser} 作为语法解析器</li>
 *   <li>在构造时自动注册所有内置函数（通过 {@link BuiltinFunctions#registerAll(EvaluationContext)}）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * StandardExpressionEngine engine = new StandardExpressionEngine();
 * engine.setVariable("order", new Order(1500.0))
 *       .setVariable("user", new User(true));
 *
 * // 内置函数可直接使用
 * Object result = engine.evaluate("max(order.amount, 1000)");  // 1500.0
 *
 * // 自定义函数也可以混用
 * engine.registerFunction("discount", args -> (double) args[0] * 0.8);
 * Object discount = engine.evaluate("discount(order.amount)"); // 1200.0
 * </pre>
 */
public class ExpressionEngine extends AbstractExpressionEngine {

    // ==================== 构造方法 ====================

    /**
     * 使用默认的 StandardEvaluationContext 和 StandardExpressionParser 构造引擎，
     * 并自动注册内置函数。
     */
    public ExpressionEngine() {
        super(); // 会调用父类构造，随后调用 initialize()
    }

    /**
     * 使用自定义的上下文和解析器构造引擎，
     * 并自动注册内置函数。
     *
     * @param context 自定义上下文（若为 null，则使用 StandardEvaluationContext）
     * @param parser  自定义解析器（若为 null，则使用 StandardExpressionParser）
     */
    public ExpressionEngine(EvaluationContext context, ExpressionParser parser) {
        super(context, parser); // 会调用父类构造，随后调用 initialize()
    }

    // ==================== 重写初始化钩子 ====================

    /**
     * 重写父类的 initialize 方法，自动注册所有内置函数。
     * <p>
     * 如果子类不希望注册内置函数，可以重写此方法为空实现，
     * 或者直接继承 {@link AbstractExpressionEngine} 并自定义初始化逻辑。
     */
    @Override
    protected void initialize() {
        // ★ 默认注册内置函数（数学、字符串、类型转换等）
        BuiltinFunctions.registerAll(context);
    }

    // ==================== 链式调用增强（返回具体类型） ====================

    /**
     * 为了保持链式调用的流畅性，重写父类方法并返回本类类型。
     * （注意：父类方法已通过 return this 实现协变返回，这里只是为了明确类型，
     *  实际编译时不需要强制重写，但为了 IDE 提示更友好，可以显式声明。）
     */

    @Override
    public ExpressionEngine setVariable(String name, Object value) {
        super.setVariable(name, value);
        return this;
    }

    @Override
    public ExpressionEngine setVariables(Map<String, Object> variables) {
        super.setVariables(variables);
        return this;
    }

    @Override
    public ExpressionEngine registerFunction(String name, FunctionResolver resolver) {
        super.registerFunction(name, resolver);
        return this;
    }
}