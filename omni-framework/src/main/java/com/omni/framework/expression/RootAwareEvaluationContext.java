package com.omni.framework.expression;

import com.omni.framework.expression.util.PropertyAccessor;
import com.omni.framework.expression.util.ReflectivePropertyAccessor;

/**
 * 根对象感知的评估上下文。
 * 当变量未显式注册时，自动从根对象上反射获取属性。
 */
public class RootAwareEvaluationContext extends StandardEvaluationContext {

    private final Object rootObject;

    public RootAwareEvaluationContext(Object rootObject) {
        this.rootObject = rootObject;
        // 同时将根对象作为普通变量 "root" 注册，支持显式 root.xxx 访问
        super.setVariable("root", rootObject);
    }

    @Override
    public Object getVariable(String name) {
        // 1. 先查显式注册的变量（优先级最高）
        if (super.getVariables().containsKey(name)) {
            return super.getVariable(name);
        }
        // 2. 根对象为 null 则跳过
        if (rootObject == null) {
            return null;
        }
        // 3. ★ 核心：从根对象上获取属性（通过反射/Getter）
        try {
            return getProperty(rootObject, name);
        } catch (Exception e) {
            // 根对象没有该属性，返回 null（表达式可自行判空）
            return null;
        }
    }

}