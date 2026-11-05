// ======== com/omni/framework/mybatis/annotation/Insert.java ========
package com.omni.framework.mybatis.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Insert {
    String value();
    int timeout() default 10;
}