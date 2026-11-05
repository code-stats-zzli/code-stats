package com.omni.framework.expression.function;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.StandardEvaluationContext;

public final class BuiltinFunctions {
    private BuiltinFunctions() {}

    public static void registerAll(EvaluationContext context) {
        // Math 常用函数
        context.registerFunction("abs", args -> Math.abs(toDouble(args[0])));
        context.registerFunction("max", args -> Math.max(toDouble(args[0]), toDouble(args[1])));
        context.registerFunction("min", args -> Math.min(toDouble(args[0]), toDouble(args[1])));
        context.registerFunction("sqrt", args -> Math.sqrt(toDouble(args[0])));
        context.registerFunction("pow", args -> Math.pow(toDouble(args[0]), toDouble(args[1])));
        context.registerFunction("random", args -> Math.random());
        // 字符串
        context.registerFunction("length", args -> args[0].toString().length());
        context.registerFunction("trim", args -> args[0].toString().trim());
        context.registerFunction("toUpperCase", args -> args[0].toString().toUpperCase());
        context.registerFunction("toLowerCase", args -> args[0].toString().toLowerCase());
        // 类型转换
        context.registerFunction("toInt", args -> Integer.valueOf(args[0].toString()));
        context.registerFunction("toDouble", args -> Double.valueOf(args[0].toString()));
        context.registerFunction("toString", args -> args[0].toString());
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return Double.parseDouble(obj.toString());
    }
}