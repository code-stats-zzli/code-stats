package com.omni.framework.expression;

import com.omni.framework.expression.function.FunctionResolver;
import com.omni.framework.expression.util.PropertyAccessor;
import com.omni.framework.expression.util.ReflectUtils;
import com.omni.framework.expression.util.ReflectivePropertyAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StandardEvaluationContext implements EvaluationContext {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, FunctionResolver> functions = new ConcurrentHashMap<>();
    private final PropertyAccessor propertyAccessor;

    public StandardEvaluationContext() {
        this.propertyAccessor = new ReflectivePropertyAccessor();
    }

    public StandardEvaluationContext(PropertyAccessor propertyAccessor) {
        this.propertyAccessor = propertyAccessor != null ? propertyAccessor : new ReflectivePropertyAccessor();
    }

    @Override
    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    @Override
    public Object getVariable(String name) {
        return variables.get(name);
    }

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Override
    public void registerFunction(String name, FunctionResolver resolver) {
        functions.put(name, resolver);
    }

    @Override
    public boolean containsFunction(String name) {
        return functions.containsKey(name);
    }

    @Override
    public Object callFunction(String name, Object... args) throws ExpressionException {
        FunctionResolver resolver = functions.get(name);
        if (resolver != null) {
            try {
                return resolver.resolve(args);
            } catch (Exception e) {
                throw new ExpressionException("Function execution failed: " + name, e);
            }
        }
        return ReflectUtils.invokeStaticMethod(name, args);
    }

    @Override
    public Object getProperty(Object target, String property) throws ExpressionException {
        return propertyAccessor.getProperty(target, property);
    }

    @Override
    public void setProperty(Object target, String property, Object value) throws ExpressionException {
        propertyAccessor.setProperty(target, property, value);
    }

    @Override
    public PropertyAccessor getPropertyAccessor() {
        return propertyAccessor;
    }
}