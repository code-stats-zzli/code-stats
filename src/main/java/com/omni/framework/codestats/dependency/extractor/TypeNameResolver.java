package com.omni.framework.codestats.dependency.extractor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;



public class TypeNameResolver {
    private final String currentPackage;
    private final Set<String> imports;      // 全限定名导入
    private final Set<String> staticImports; // 静态导入暂忽略

    public TypeNameResolver(String currentPackage, List<String> imports) {
        this.currentPackage = currentPackage == null ? "" : currentPackage;
        this.imports = new HashSet<>();
        this.staticImports = new HashSet<>();
        for (String imp : imports) {
            if (imp.endsWith(".*")) {
                // 按需导入，不处理通配符
                continue;
            }
            if (imp.startsWith("static ")) {
                staticImports.add(imp.substring(7));
            } else {
                this.imports.add(imp);
            }
        }
    }

    /**
     * 将简单类型名或部分限定名解析为全限定名
     */
    public String resolve(String typeName) {
        if (typeName == null || typeName.isEmpty()) return null;
        // 去除数组后缀
        String baseName = typeName.replaceAll("\\[\\]$", "");
        // 处理泛型擦除，只取外层类名
        int genericStart = baseName.indexOf('<');
        if (genericStart > 0) {
            baseName = baseName.substring(0, genericStart);
        }

        // 1. 已经是全限定名（包含点且不在导入中精确匹配）
        if (baseName.contains(".")) {
            // 可能是完全限定名，直接返回
            return baseName;
        }

        // 2. 精确匹配导入
        for (String imp : imports) {
            if (imp.endsWith("." + baseName)) {
                return imp;
            }
        }

        // 3. java.lang 隐式导入
        if (isJavaLangClass(baseName)) {
            return "java.lang." + baseName;
        }

        // 4. 同包下
        if (!currentPackage.isEmpty()) {
            return currentPackage + "." + baseName;
        }

        // 5. 原始类型或无法解析，返回 null 标记
        return null;
    }

    private boolean isJavaLangClass(String name) {
        return Set.of("Object", "String", "Integer", "Long", "Double", "Float", "Boolean", "Character",
                "Byte", "Short", "Void", "Class", "System", "Thread", "Exception", "RuntimeException",
                "Throwable", "Iterable", "Comparable", "Runnable", "Enum").contains(name);
    }

    public static boolean isPrimitive(String typeName) {
        return Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char", "void")
                .contains(typeName);
    }
}