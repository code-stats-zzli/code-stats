// 文件: src/main/java/com/omni/framework/spring/mvc/argument/ArgumentResolverFactory.java
package com.omni.framework.spring.mvc.argument;

public final class ArgumentResolverFactory {
    private ArgumentResolverFactory() {
    }

    public static ArgumentResolverComposite createDefault() {
        ArgumentResolverComposite composite = new ArgumentResolverComposite();

        composite.addResolver(new ServletRequestArgumentResolver());
        composite.addResolver(new ServletResponseArgumentResolver());
        composite.addResolver(new PathVariableArgumentResolver());
        composite.addResolver(new RequestParamMapArgumentResolver());
        composite.addResolver(new RequestParamArgumentResolver());
        composite.addResolver(new RequestHeaderArgumentResolver());
        composite.addResolver(new CookieValueArgumentResolver());
        composite.addResolver(new RequestAttributeArgumentResolver());
        composite.addResolver(new SessionAttributeArgumentResolver());
        composite.addResolver(new RequestPartArgumentResolver());
        composite.addResolver(new RequestBodyArgumentResolver());
        composite.addResolver(new ModelAttributeArgumentResolver());
        composite.addResolver(new SimpleTypeArgumentResolver());

        return composite;
    }
}