package com.omni.framework.codestats.dependency.model;

import java.util.List;

/**
 * 方法元数据（方法级调用图的节点）。
 */
public class MethodInfo {
    private final String id;                 // 例: com.example.AuthService.login(String,String)
    private final String className;
    private final String methodName;
    private final List<String> parameterTypes;
    private final String returnType;
    private final int lineNumber;
    private final boolean constructor;
    private final boolean isStatic;

    public MethodInfo(String id, String className, String methodName,
                      List<String> parameterTypes, String returnType,
                      int lineNumber, boolean constructor, boolean isStatic) {
        this.id = id;
        this.className = className;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
        this.lineNumber = lineNumber;
        this.constructor = constructor;
        this.isStatic = isStatic;
    }

    public String getId() { return id; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public List<String> getParameterTypes() { return parameterTypes; }
    public String getReturnType() { return returnType; }
    public int getLineNumber() { return lineNumber; }
    public boolean isConstructor() { return constructor; }
    public boolean isStatic() { return isStatic; }

    public String getSimpleClassName() {
        return className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
    }

    public String getDisplayName() {
        return getSimpleClassName() + "." + methodName
                + "(" + String.join(",", parameterTypes) + ")";
    }
}