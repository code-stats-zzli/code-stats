package com.omni.framework.expression.util;

import com.omni.framework.expression.ExpressionException;

public interface PropertyAccessor {
    Object getProperty(Object target, String property) throws ExpressionException;
    void setProperty(Object target, String property, Object value) throws ExpressionException;
}