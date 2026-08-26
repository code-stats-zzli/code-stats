package com.omni.framework.expression.function;

@FunctionalInterface
public interface FunctionResolver {
    Object resolve(Object... args) throws Exception;
}