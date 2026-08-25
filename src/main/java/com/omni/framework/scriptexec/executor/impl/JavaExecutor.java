package com.omni.framework.scriptexec.executor.impl;



import com.omni.framework.scriptexec.exception.ScriptExecutionException;
import com.omni.framework.scriptexec.executor.BaseScriptExecutor;
import com.omni.framework.scriptexec.executor.model.ScriptSource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JavaExecutor extends BaseScriptExecutor {

    @Override
    protected String getDefaultLanguage() {
        return "java";
    }

    @Override
    protected void prepareExecution(ScriptSource source, Map<String, Object> params) throws Exception {
        String mode = source.getExecutionMode();
        if ("source".equals(mode)) {
            boolean needCompile = (Boolean) params.getOrDefault("needCompile", true);
            // 如果有路径且是 .java 文件，读取内容；否则使用 content
            String content;
            if (source.getPath() != null && Files.exists(source.getPath())) {
                content = new String(Files.readAllBytes(source.getPath()));
                source.setContent(content);
            } else {
                content = source.getContent();
                if (content == null) {
                    throw new ScriptExecutionException("No content for Java source");
                }
            }
            if (!needCompile) {
                throw new ScriptExecutionException("Java source mode requires needCompile=true");
            }
            String className = extractClassName(content);
            if (className == null) {
                throw new ScriptExecutionException("Cannot determine class name from source");
            }
            // 创建临时目录存放 .java 和 .class
            Path srcDir = Files.createTempDirectory("javasrc_");
            Path javaFile = srcDir.resolve(className + ".java");
            Files.write(javaFile, content.getBytes());
            // 编译
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                ProcessBuilder pb = new ProcessBuilder("javac", javaFile.toString());
                pb.redirectErrorStream(true);
                int code = pb.start().waitFor();
                if (code != 0) throw new ScriptExecutionException("javac failed");
            } else {
                int result = compiler.run(null, null, null, javaFile.toString());
                if (result != 0) throw new ScriptExecutionException("Compilation failed");
            }
            source.setCompiledArtifact(srcDir);
            source.setCompiled(true);
            params.put("_className", className);
        } else if ("binary".equals(mode) || "jar".equals(mode)) {
            // 优先使用路径
            if (source.getPath() != null && Files.exists(source.getPath())) {
                source.setCompiledArtifact(source.getPath());
                source.setCompiled(true);
                return;
            }
            // 无路径，从内容写入临时文件
            String content = source.getContent();
            if (content == null) {
                throw new ScriptExecutionException("No content for " + mode);
            }
            String suffix = "jar".equals(mode) ? ".jar" : ".class";
            Path tempFile = createTempFile(content, suffix);
            source.setCompiledArtifact(tempFile);
            source.setCompiled(true);
        } else if ("directory".equals(mode)) {
            // 目录模式已在 resolve 中处理路径，这里直接使用
            Path dir = source.getPath();
            if (dir == null) {
                throw new ScriptExecutionException("Directory path missing");
            }
            // 若 needCompile=true，编译目录下所有 .java
            Boolean needCompile = (Boolean) params.getOrDefault("needCompile", false);
            if (needCompile) {
                ProcessBuilder pb = new ProcessBuilder("javac", "-d", dir.toString());
                Files.walk(dir).filter(p -> p.toString().endsWith(".java")).forEach(p -> pb.command().add(p.toString()));
                pb.redirectErrorStream(true);
                int code = pb.start().waitFor();
                if (code != 0) throw new ScriptExecutionException("Directory compilation failed");
            }
            source.setCompiledArtifact(dir);
            source.setCompiled(true);
        } else {
            throw new ScriptExecutionException("Unsupported execution mode: " + mode);
        }
    }

    @Override
    protected String doExecute(ScriptSource source, Map<String, Object> params) throws Exception {
        String mode = source.getExecutionMode();
        if ("jar".equals(mode)) {
            String jarPath = source.getCompiledArtifact().toString();
            String className = (String) params.get("className");
            String methodName = (String) params.get("methodName");
            if (className != null && methodName != null) {
                return executeJarMethod(jarPath, className, methodName, params);
            } else {
                return executeJarFile(jarPath, params);
            }
        } else if ("binary".equals(mode) || "directory".equals(mode) || "source".equals(mode)) {
            String classpath;
            String className;
            if ("source".equals(mode)) {
                classpath = source.getCompiledArtifact().toString();
                className = (String) params.get("_className");
            } else {
                classpath = source.getCompiledArtifact().toString();
                className = (String) params.get("className");
                if (className == null) {
                    throw new ScriptExecutionException("className required for " + mode + " mode");
                }
            }
            return executeClass(classpath, className, params);
        }
        throw new ScriptExecutionException("Cannot execute in mode: " + mode);
    }

    // ---------- 内部执行方法 ----------
    private String executeJarFile(String jarPath, Map<String, Object> params) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath);
        addArgs(pb, params);
        return runProcess(pb, 30);
    }

    /**
     * 改进后的反射调用 JAR 中的方法：
     * - 根据方法名查找所有公共方法
     * - 如果只有一个方法，直接使用，并尝试转换参数
     * - 如果有多个重载，先按参数个数匹配，再按类型兼容性匹配
     * - 参数从 params 的 "args" 键获取（支持 Object[] 或 List）
     * - 若 "args" 不存在，但存在 "arg"，则作为单参数向后兼容
     * - 在调用前对参数个数和类型进行校验，确保抛出清晰异常
     */
    private String executeJarMethod(String jarPath, String className, String methodName, Map<String, Object> params) throws Exception {
        URL jarUrl = new File(jarPath).toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl})) {
            Class<?> clazz = loader.loadClass(className);
            // 获取所有公共方法
            Method[] methods = clazz.getMethods();
            List<Method> candidates = Arrays.stream(methods)
                    .filter(m -> m.getName().equals(methodName))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                throw new ScriptExecutionException("No method found with name: " + methodName);
            }

            // 提取参数列表
            Object argsObj = params.get("args");
            List<Object> argList = new ArrayList<>();
            if (argsObj instanceof Object[]) {
                argList.addAll(Arrays.asList((Object[]) argsObj));
            } else if (argsObj instanceof List) {
                argList.addAll((List<?>) argsObj);
            } else if (argsObj != null) {
                argList.add(argsObj);
            }
            // 向后兼容：如果没有 args 但有 arg，则使用单参数
            if (argList.isEmpty() && params.containsKey("arg")) {
                argList.add(params.get("arg"));
            }

            Method selectedMethod = null;
            // 如果只有一个候选，直接使用
            if (candidates.size() == 1) {
                selectedMethod = candidates.get(0);
            } else {
                // 多个候选，先按参数个数匹配
                List<Method> matchedByCount = candidates.stream()
                        .filter(m -> m.getParameterCount() == argList.size())
                        .collect(Collectors.toList());
                if (matchedByCount.isEmpty()) {
                    throw new ScriptExecutionException("No method with " + argList.size() + " parameters found for method: " + methodName);
                }
                if (matchedByCount.size() == 1) {
                    selectedMethod = matchedByCount.get(0);
                } else {
                    // 多个方法参数个数相同，尝试类型兼容匹配
                    for (Method m : matchedByCount) {
                        if (canConvertArguments(argList, m.getParameterTypes())) {
                            if (selectedMethod == null) {
                                selectedMethod = m;
                            } else {
                                throw new ScriptExecutionException("Multiple methods with same parameter count and compatible types for method: " + methodName);
                            }
                        }
                    }
                    if (selectedMethod == null) {
                        throw new ScriptExecutionException("Cannot find a method with compatible parameter types for method: " + methodName);
                    }
                }
            }

            // ---------- 统一校验选中的方法 ----------
            if (selectedMethod == null) {
                throw new ScriptExecutionException("No suitable method found: " + methodName);
            }

            // 1. 校验参数个数
            if (selectedMethod.getParameterCount() != argList.size()) {
                throw new ScriptExecutionException(
                        String.format("Parameter count mismatch for method '%s': expected %d, got %d",
                                methodName, selectedMethod.getParameterCount(), argList.size())
                );
            }

            // 2. 校验参数类型兼容性（提前校验，避免转换时抛出模糊异常）
            if (!canConvertArguments(argList, selectedMethod.getParameterTypes())) {
                throw new ScriptExecutionException(
                        String.format("Parameter types incompatible for method '%s'. Expected types: %s, provided args: %s",
                                methodName,
                                Arrays.toString(selectedMethod.getParameterTypes()),
                                argList.stream().map(Object::getClass).map(Class::getSimpleName).collect(Collectors.toList())
                        )
                );
            }

            // 3. 转换参数并调用（此时保证数量和类型都匹配）
            Class<?>[] paramTypes = selectedMethod.getParameterTypes();
            Object[] convertedArgs = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                convertedArgs[i] = convertToType(argList.get(i), paramTypes[i]);
            }

            Object result = selectedMethod.invoke(null, convertedArgs);
            return result != null ? result.toString() : "";
        }
    }

    /**
     * 检查参数列表是否能转换为目标类型数组（仅做简单检查，不实际转换）
     */
    private boolean canConvertArguments(List<Object> args, Class<?>[] targetTypes) {
        if (args.size() != targetTypes.length) return false;
        for (int i = 0; i < args.size(); i++) {
            try {
                convertToType(args.get(i), targetTypes[i]);
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将任意值转换为目标类型（支持基本类型及常见包装类、String）
     */
    private Object convertToType(Object value, Class<?> targetType) throws ScriptExecutionException {
        if (value == null) {
            // 基本类型不允许 null
            if (targetType.isPrimitive()) {
                throw new ScriptExecutionException("Cannot convert null to primitive type: " + targetType);
            }
            return null;
        }
        // 如果已经是目标类型或可赋值
        if (targetType.isInstance(value)) {
            return value;
        }
        // 转换为字符串
        String str = value.toString();
        if (targetType == String.class) {
            return str;
        }
        // 基本类型和包装类型
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(str);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(str);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(str);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(str);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(str);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(str);
        }
        if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(str);
        }
        if (targetType == char.class || targetType == Character.class) {
            if (str.length() == 1) {
                return str.charAt(0);
            }
            throw new ScriptExecutionException("Cannot convert '" + str + "' to char");
        }
        // 其他类型：尝试使用 String 参数构造
        try {
            return targetType.getConstructor(String.class).newInstance(str);
        } catch (Exception e) {
            throw new ScriptExecutionException("Cannot convert value '" + str + "' to type " + targetType.getName(), e);
        }
    }

    private String executeClass(String classpath, String className, Map<String, Object> params) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpath, className);
        addArgs(pb, params);
        return runProcess(pb, 30);
    }



    private String extractClassName(String code) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)");
        java.util.regex.Matcher m = p.matcher(code);
        if (m.find()) return m.group(1);
        p = java.util.regex.Pattern.compile("class\\s+(\\w+)");
        m = p.matcher(code);
        return m.find() ? m.group(1) : null;
    }
}