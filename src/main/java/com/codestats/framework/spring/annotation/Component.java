package com.codestats.framework.spring.annotation;

import java.lang.annotation.*;

/**
 * 通用组件注解，标记一个类为 Spring 管理的 Bean。
 * 功能与 @Service、@Repository、@Controller 类似，但语义更通用。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Component {
}