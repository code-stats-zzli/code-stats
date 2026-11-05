// ======== com/omni/framework/mybatis/annotation/Delete.java ========
package com.omni.framework.mybatis.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Delete {
    String value();
    int timeout() default 10;
}