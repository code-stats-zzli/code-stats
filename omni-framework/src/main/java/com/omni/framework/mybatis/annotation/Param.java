// ======== com/omni/framework/mybatis/annotation/Param.java ========
package com.omni.framework.mybatis.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    String value();
}