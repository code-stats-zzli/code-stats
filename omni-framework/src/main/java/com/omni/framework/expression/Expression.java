package com.omni.framework.expression;

public interface Expression {
    /**
     * 在给定上下文中求值，返回结果
     */
    Object getValue(EvaluationContext context) throws ExpressionException;

    /**
     * 获取原始表达式字符串
     */
    String getExpressionString();
}