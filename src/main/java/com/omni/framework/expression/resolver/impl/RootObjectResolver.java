package com.omni.framework.expression.resolver.impl;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.RootObject;
import com.omni.framework.expression.resolver.VariableResolver;
import com.omni.framework.expression.util.PropertyAccessor;
import com.omni.framework.expression.util.ReflectivePropertyAccessor;

/**
 * 从根对象获取属性。
 * 支持两种模式：
 * <ul>
 *   <li>若根对象实现了 {@link RootObject} 接口，则调用 {@code getProperty(name)}</li>
 *   <li>否则通过反射（getter/字段）获取</li>
 * </ul>
 * 优先级：4（中等）
 */
public class RootObjectResolver implements VariableResolver {

    private final Object rootObject;
    private final PropertyAccessor propertyAccessor;

    public RootObjectResolver(Object rootObject) {
        this.rootObject = rootObject;
        this.propertyAccessor = new ReflectivePropertyAccessor();
    }

    @Override
    public Object resolve(String name, EvaluationContext context) {
        if (rootObject == null) {
            return null;
        }

        if (rootObject instanceof RootObject) {
            return ((RootObject) rootObject).getProperty(name);
        }

        try {
            return propertyAccessor.getProperty(rootObject, name);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getPriority() {
        return 4; // 内置优先级：4（根对象）
    }
}