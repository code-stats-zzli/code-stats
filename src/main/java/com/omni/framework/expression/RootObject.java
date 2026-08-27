package com.omni.framework.expression;



/**
 * 自定义根对象接口。
 * 实现此接口可自定义属性访问逻辑（如动态计算、懒加载、从数据库取数等）。
 */
@FunctionalInterface
public interface RootObject {
    /**
     * 获取指定名称的属性值。
     *
     * @param name 属性名（表达式中的标识符）
     * @return 属性值（若不存在可返回 null）
     */
    Object getProperty(String name);
}
