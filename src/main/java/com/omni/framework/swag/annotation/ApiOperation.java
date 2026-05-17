package com.omni.framework.swag.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiOperation {
    String value() default "";          // 简要描述
    String notes() default "";          // 详细说明
    String nickname() default "";       // 操作ID
}