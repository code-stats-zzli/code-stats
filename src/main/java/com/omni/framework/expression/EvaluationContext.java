package com.omni.framework.expression;

import com.omni.framework.expression.function.FunctionResolver;
import com.omni.framework.expression.util.PropertyAccessor;

import java.util.Map;

public interface EvaluationContext {
    void setVariable(String name, Object value);

    Object getVariable(String name);

    Map<String, Object> getVariables();

    void registerFunction(String name, FunctionResolver resolver);

    Object callFunction(String name, Object... args) throws ExpressionException;

    Object getProperty(Object target, String property) throws ExpressionException;

    void setProperty(Object target, String property, Object value) throws ExpressionException;

    PropertyAccessor getPropertyAccessor();
}