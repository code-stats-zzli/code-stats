package com.omni.framework.expression;

import com.omni.framework.expression.function.FunctionResolver;

import java.util.Map;

/**
 * 表达式引擎抽象类，封装了核心的上下文管理、函数注册和表达式求值流程。
 * <p>
 * 子类只需关注如何初始化上下文和解析器，以及是否需要预置内置函数。
 * <p>
 * 使用示例（通过子类 StandardExpressionEngine）：
 * <pre>
 * AbstractExpressionEngine engine = new StandardExpressionEngine()
 *         .setVariable("user", new User("张三", 25))
 *         .setVariables(Map.of("a", 10, "b", 20))
 *         .registerFunction("double", args -> (int) args[0] * 2);
 *
 * Object result = engine.evaluate("user.name + '的年龄是' + user.age"); // 张三的年龄是25
 * </pre>
 */
public abstract class AbstractExpressionEngine {

    /**
     * 运行时上下文（管理变量、函数、属性访问）
     */
    protected final EvaluationContext context;

    /**
     * 表达式解析器（词法分析 + 语法分析 + AST 构建）
     */
    protected final ExpressionParser parser;

    // ==================== 构造方法 ====================

    /**
     * 使用默认的上下文和解析器构造引擎。
     * 子类可调用此构造方法，并在子类构造中完成额外初始化（如注册内置函数）。
     */
    protected AbstractExpressionEngine() {
        this(new StandardEvaluationContext(), new StandardExpressionParser());
    }

    /**
     * 使用自定义的上下文和解析器构造引擎。
     * 子类可调用此构造方法传入自定义组件。
     *
     * @param context 自定义上下文（若为 null，则自动使用 StandardEvaluationContext）
     * @param parser  自定义解析器（若为 null，则自动使用 StandardExpressionParser）
     */
    protected AbstractExpressionEngine(EvaluationContext context, ExpressionParser parser) {
        this.context = (context != null) ? context : new StandardEvaluationContext();
        this.parser = (parser != null) ? parser : new StandardExpressionParser();
        // ★ 调用初始化钩子（子类可重写，用于注册内置函数或自定义初始化）
        initialize();
    }

    // ==================== 初始化钩子（扩展点） ====================

    /**
     * 初始化钩子，在构造方法最后被调用。
     * <p>
     * 子类可以重写此方法，用于注册内置函数、设置默认变量等。
     * 例如：{@link StandardExpressionEngine} 在此方法中调用 {@link BuiltinFunctions#registerAll(EvaluationContext)}。
     * <p>
     * 默认实现为空。
     */
    protected void initialize() {
        // 默认无操作，留给子类扩展
    }

    // ==================== 变量注册（支持单个 + 批量 Map） ====================

    /**
     * 注册单个变量到上下文中。
     *
     * @param name  变量名
     * @param value 变量值（可以是 POJO、集合、基本类型等）
     * @return 当前引擎实例（支持链式调用）
     */
    public AbstractExpressionEngine setVariable(String name, Object value) {
        context.setVariable(name, value);
        return this;
    }

    /**
     * 批量注册变量（从 Map 中导入）。
     *
     * @param variables 变量名 -> 变量值 的 Map
     * @return 当前引擎实例（支持链式调用）
     */
    public AbstractExpressionEngine setVariables(Map<String, Object> variables) {
        if (variables != null) {
            variables.forEach(context::setVariable);
        }
        return this;
    }

    // ==================== 函数注册 ====================

    /**
     * 注册自定义函数到上下文中。
     *
     * @param name     函数名（表达式中调用的名称）
     * @param resolver 函数实现（函数式接口，支持 Lambda）
     * @return 当前引擎实例（支持链式调用）
     */
    public AbstractExpressionEngine registerFunction(String name, FunctionResolver resolver) {
        context.registerFunction(name, resolver);
        return this;
    }

    // ==================== 解析与求值 ====================

    /**
     * 解析表达式字符串，返回 {@link Expression} 对象。
     * 后续可通过 {@link Expression#getValue(EvaluationContext)} 传入上下文求值。
     *
     * @param expression 表达式字符串
     * @return 解析后的表达式对象
     */
    public Expression parse(String expression) {
        return parser.parse(expression);
    }

    /**
     * 直接求值表达式字符串，内部自动使用当前上下文。
     * 这是最常用的快捷方法。
     *
     * @param expression 表达式字符串
     * @return 求值结果
     */
    public Object evaluate(String expression) {
        return parse(expression).getValue(context);
    }

    // ==================== 访问器（供子类或外部获取底层组件） ====================

    /**
     * 获取内部的 EvaluationContext，方便进行更高级的操作（如直接获取变量列表）。
     */
    public EvaluationContext getContext() {
        return context;
    }

    /**
     * 获取内部的 ExpressionParser，方便获取解析器扩展功能。
     */
    public ExpressionParser getParser() {
        return parser;
    }
}