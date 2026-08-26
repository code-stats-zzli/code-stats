package com.omni.framework.expression;

public interface ExpressionParser {
    Expression parse(String expression) throws ExpressionException;
}