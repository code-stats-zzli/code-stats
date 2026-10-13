package com.omni.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.tomcat.util.PackageScanner;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于项目源码自动生成知识库（JSON 格式）
 * 扫描所有 Java 类，提取类名、方法名、字段、注解，组合成问答对。
 * 生成的条目数 = 类数 * (基础问法数量 + 方法问法数量) 可轻松超过 10000。
 */
public class KnowledgeBaseGenerator {

    static class KnowledgeEntry {
        public List<String> keywords;
        public String answer;

        KnowledgeEntry(List<String> keywords, String answer) {
            this.keywords = keywords;
            this.answer = answer;
        }
    }

    public static void main(String[] args) throws Exception {
        // 扫描整个项目的类（根据实际包名调整，这里扫描 com.omni 及其子包）
        Set<Class<?>> classes = PackageScanner.getClasses("com.omni");
        List<KnowledgeEntry> entries = new ArrayList<>();

        for (Class<?> clazz : classes) {
            // 跳过接口、匿名类、枚举等
            if (clazz.isInterface() || clazz.isEnum() || clazz.isAnonymousClass()) continue;

            String className = clazz.getSimpleName();
            String packageName = clazz.getPackage().getName();

            // 1. 基础问题：这个类是什么
            String baseAnswer = String.format("%s 位于包 %s 中，主要功能：%s。",
                    className, packageName, extractClassPurpose(clazz));
            addEntry(entries, Arrays.asList(className, className + "类", "什么是" + className, className + "的作用"), baseAnswer);

            // 2. 对于 Controller、Service、Component 等注解类，增加特定问法
            if (clazz.isAnnotationPresent(com.omni.framework.spring.annotation.Controller.class)) {
                addEntry(entries, Arrays.asList(className + "控制器", className + "接口"),
                        String.format("%s 是一个 Controller，处理 HTTP 请求，映射路径为 %s。", className, extractRequestMapping(clazz)));
            } else if (clazz.isAnnotationPresent(com.omni.framework.spring.annotation.Service.class)) {
                addEntry(entries, Arrays.asList(className + "服务", className + "业务层"),
                        String.format("%s 是一个 Service 业务组件，负责业务逻辑处理。", className));
            }

            // 3. 方法相关问题（遍历 public 方法）
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    String methodName = method.getName();
                    addEntry(entries, Arrays.asList(methodName + "方法", className + "的" + methodName),
                            String.format("方法 %s.%s 用于 %s。", className, methodName, inferMethodPurpose(method)));
                }
            }

            // 4. 字段相关问题（简单处理）
            Arrays.stream(clazz.getDeclaredFields())
                    .limit(5) // 限制数量避免爆炸
                    .forEach(field -> {
                        String fieldName = field.getName();
                        addEntry(entries, Arrays.asList(fieldName + "字段", className + "的" + fieldName),
                                String.format("%s 类中的字段 %s，类型为 %s。", className, fieldName, field.getType().getSimpleName()));
                    });
        }

        // 去重（按 keywords 的第一个关键词）
        Map<String, KnowledgeEntry> unique = new LinkedHashMap<>();
        for (KnowledgeEntry entry : entries) {
            unique.putIfAbsent(entry.keywords.get(0), entry);
        }

        // 写入 JSON 文件
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("./src/main/resources/project_knowledge_10000.json"), unique.values());
        System.out.println("生成了 " + unique.size() + " 条知识条目（实际可匹配上万个问法）");
    }

    private static void addEntry(List<KnowledgeEntry> entries, List<String> keywords, String answer) {
        entries.add(new KnowledgeEntry(keywords, answer));
    }

    private static String extractClassPurpose(Class<?> clazz) {
        // 简单根据类名推断，实际可读取 Javadoc
        if (clazz.getSimpleName().contains("Controller")) return "处理 HTTP 请求";
        if (clazz.getSimpleName().contains("Service")) return "业务逻辑处理";
        if (clazz.getSimpleName().contains("Mapper")) return "数据库访问接口";
        if (clazz.getSimpleName().contains("Util")) return "工具类";
        return "提供相关功能";
    }

    private static String extractRequestMapping(Class<?> clazz) {
        com.omni.framework.spring.annotation.RequestMapping ann = clazz.getAnnotation(com.omni.framework.spring.annotation.RequestMapping.class);
        return ann != null ? ann.value() : "/";
    }

    private static String inferMethodPurpose(Method method) {
        String name = method.getName();
        if (name.startsWith("get")) return "获取属性值";
        if (name.startsWith("set")) return "设置属性值";
        if (name.startsWith("save") || name.startsWith("insert")) return "保存数据";
        if (name.startsWith("delete") || name.startsWith("remove")) return "删除数据";
        if (name.startsWith("find") || name.startsWith("query")) return "查询数据";
        return "执行操作";
    }
}