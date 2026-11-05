package com.omni.framework.swag.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiModule {
    String value();          // 模块名称
    String description() default "";
}