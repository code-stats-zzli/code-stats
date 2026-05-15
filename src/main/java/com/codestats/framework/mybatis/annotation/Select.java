package com.codestats.framework.mybatis.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Select {
    String value();
    int timeout() default 10;   // 新增：超时时间（秒），-1 表示不设置
}