package com.omni.framework.codestats.dependency.extractor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TypeNameResolver {
    private final String currentPackage;
    private final Set<String> imports;
    private final Set<String> staticImports;
    // ★ [P1] 通配符 import，例如 java.util.*
    private final Set<String> wildcardImports;

    public TypeNameResolver(String currentPackage, List<String> imports) {
        this.currentPackage = currentPackage == null ? "" : currentPackage;
        this.imports = new HashSet<>();
        this.staticImports = new HashSet<>();
        this.wildcardImports = new HashSet<>();
        for (String imp : imports) {
            if (imp == null) continue;
            if (imp.startsWith("static ")) {
                staticImports.add(imp.substring(7));
            } else if (imp.endsWith(".*")) {
                wildcardImports.add(imp.substring(0, imp.length() - 2));
            } else {
                this.imports.add(imp);
            }
        }
    }

    public String resolve(String typeName) {
        if (typeName == null || typeName.isEmpty()) return null;
        String baseName = typeName.replaceAll("\\[\\]$", "");
        int genericStart = baseName.indexOf('<');
        if (genericStart > 0) baseName = baseName.substring(0, genericStart);

        if (baseName.contains(".")) return baseName;

        // 精确导入
        for (String imp : imports) {
            if (imp.endsWith("." + baseName)) return imp;
        }

        // java.lang 隐式导入
        if (isJavaLangClass(baseName)) return "java.lang." + baseName;

        // ★ [P1] 通配符 import 兜底：返回第一个命中的全限定名
        for (String pkg : wildcardImports) {
            return pkg + "." + baseName;
        }

        // 同包
        if (!currentPackage.isEmpty()) return currentPackage + "." + baseName;

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