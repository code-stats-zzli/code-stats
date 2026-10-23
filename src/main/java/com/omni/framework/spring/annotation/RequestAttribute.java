// 文件: src/main/java/com/omni/framework/spring/annotation/RequestAttribute.java
package com.omni.framework.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestAttribute {
    String value() default "";
    boolean required() default true;
    String defaultValue() default "";
}