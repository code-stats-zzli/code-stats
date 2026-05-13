package com.framework.tomcat.util;

import java.io.File;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PackageScanner {

    /**
     * 扫描匹配模式（支持 * 和 **）的包下的所有类
     *
     * @param pattern 示例：com.demo.*.controller 或 com.demo.**
     * @return 匹配的类集合
     */
    public static Set<Class<?>> scan(String pattern) throws Exception {
        String basePackage = getBasePackage(pattern);
        Set<Class<?>> allClasses = getClassesRecursive(basePackage);
        Set<Class<?>> result = new HashSet<>();

        // 构造正则：将 * 替换为 [^.]+，** 替换为 .*
        String regex = buildRegex(pattern);

        for (Class<?> clazz : allClasses) {
            if (clazz.getName().matches(regex)) {
                result.add(clazz);
            }
        }
        return result;
    }

    // 从模式构建正则表达式
    private static String buildRegex(String pattern) {
        // 先转义点号，再处理通配符（注意顺序：先占位 **，再处理单 *）
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", "##STARSTAR##")   // 占位
                .replace("*", "[^.]+")
                .replace("##STARSTAR##", ".*");
        // 匹配包括最后一层具体类名
        return "^" + regex + "(\\.[^.]+)?$";
    }

    // 提取基础包名（取第一个通配符前的部分）
    private static String getBasePackage(String pattern) {
        int starIdx = pattern.indexOf('*');
        if (starIdx == -1) {
            starIdx = pattern.indexOf('?');
        }
        if (starIdx == -1) {
            return pattern;
        }
        // 截取到最后一个完整的包段（即通配符前最近的 '.' 之后）
        String prefix = pattern.substring(0, starIdx);
        if (prefix.endsWith(".")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    // 递归扫描包及其子包（文件系统与JAR）
    private static Set<Class<?>> getClassesRecursive(String packageName) throws Exception {
        Set<Class<?>> classes = new HashSet<>();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if ("file".equals(resource.getProtocol())) {
                File dir = new File(resource.getFile());
                collectClassesFromDir(dir, packageName, classes);
            } else if ("jar".equals(resource.getProtocol())) {
                // 处理 jar 包中的类
                String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                try (JarFile jar = new JarFile(jarPath)) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".class") && name.startsWith(path)) {
                            String className = name.substring(0, name.length() - 6).replace('/', '.');
                            addClassSafely(classes, className);
                        }
                    }
                }
            }
        }
        return classes;
    }

    private static void collectClassesFromDir(File dir, String packageName, Set<Class<?>> classes) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectClassesFromDir(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                addClassSafely(classes, className);
            }
        }
    }

    // 安全加载类，忽略任何加载/初始化异常
    private static void addClassSafely(Set<Class<?>> classes, String className) {
        try {
            classes.add(Class.forName(className));
        } catch (Throwable ignored) {
            // 忽略无法加载的类（如缺失依赖、初始化错误等）
        }
    }

    // 兼容旧方法（支持逗号分隔的多个模式或固定包名）
    public static Set<Class<?>> getClasses(String... packageNames) throws Exception {
        Set<Class<?>> allClasses = new HashSet<>();
        for (String pkg : packageNames) {
            if (pkg.contains("*") || pkg.contains("?")) {
                allClasses.addAll(scan(pkg));
            } else {
                allClasses.addAll(getClassesRecursive(pkg));
            }
        }
        return allClasses;
    }
}