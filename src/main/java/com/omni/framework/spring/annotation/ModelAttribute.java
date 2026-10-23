// 文件: src/main/java/com/omni/framework/spring/annotation/ModelAttribute.java
package com.omni.framework.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModelAttribute {
    String value() default "";
    boolean required() default true;
}