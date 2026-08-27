package com.omni.framework.expression.resolver.impl;


import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.resolver.VariableResolver;
import com.omni.framework.expression.util.PropertyAccessor;
import com.omni.framework.expression.util.ReflectivePropertyAccessor;

/**
 * 从根对象（POJO）上通过反射获取属性。
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
        try {
            return propertyAccessor.getProperty(rootObject, name);
        } catch (Exception e) {
            return null;
        }
    }
}
