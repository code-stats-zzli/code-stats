// 文件: src/main/java/com/omni/framework/spring/annotation/SessionAttribute.java
package com.omni.framework.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SessionAttribute {
    String value() default "";
    boolean required() default true;
    String defaultValue() default "";
}