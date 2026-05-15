package com.codestats.framework.tomcat.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 存储 Servlet 映射关系（URL -> 全限定类名）。
 */
public class ServletMapping {

    private static final Map<String, String> mappings = new HashMap<>();

    public static void addMapping(String urlPattern, String className) {
        mappings.put(urlPattern, className);
    }

    public static String getServletClass(String uri) {
        // 简单精确匹配，例如 /hello
        return mappings.get(uri);
    }
}