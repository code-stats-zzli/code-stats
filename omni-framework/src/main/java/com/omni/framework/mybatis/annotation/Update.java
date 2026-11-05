// ======== com/omni/framework/mybatis/annotation/Update.java ========
package com.omni.framework.mybatis.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Update {
    String value();
    int timeout() default 10;
}