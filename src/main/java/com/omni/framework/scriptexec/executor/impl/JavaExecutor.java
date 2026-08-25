package com.omni.framework.scriptexec.executor.impl;

import com.omni.framework.scriptexec.exception.ScriptExecutionException;
import com.omni.framework.scriptexec.executor.BaseScriptExecutor;
import com.omni.framework.scriptexec.executor.model.ScriptSource;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
            // 获取源码内容
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

            // 提取完整类名（含包名）
            String className = extractClassName(content);
            if (className == null) {
                throw new ScriptExecutionException("Cannot determine class name from source");
            }
            String simpleName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;

            // 创建临时源文件目录
            Path srcDir = Files.createTempDirectory("javasrc_");
            Path javaFile = srcDir.resolve(simpleName + ".java");
            Files.write(javaFile, content.getBytes());

            // 创建独立的输出目录（用于存放 .class，保留包结构）
            Path outputDir = Files.createTempDirectory("javaclass_");

            // ====== 编译：优先使用 JavaCompiler API ======
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                // 降级：尝试使用命令行 javac（可能因权限问题失败）
                try {
                    ProcessBuilder pb = new ProcessBuilder("javac", "-d", outputDir.toString(), javaFile.toString());
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    String compileOutput = readStream(process.getInputStream());
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new ScriptExecutionException(
                                "Compilation failed with exit code " + exitCode +
                                        "\n=== Compiler output ===\n" + compileOutput
                        );
                    }
                } catch (IOException e) {
                    throw new ScriptExecutionException(
                            "Cannot execute javac. Please ensure JDK is installed and javac is in PATH, " +
                                    "or use a JRE with tools.jar available. Error: " + e.getMessage(), e
                    );
                }
            } else {
                // 使用 JavaCompiler API 编译
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(javaFile.toFile());

                // 设置编译选项：输出目录
                List<String> options = new ArrayList<>();
                options.add("-d");
                options.add(outputDir.toString());

                JavaCompiler.CompilationTask task = compiler.getTask(
                        null, fileManager, diagnostics, options, null, compilationUnits
                );

                boolean success = task.call();
                fileManager.close();

                if (!success) {
                    // 收集诊断信息
                    StringBuilder errorMsg = new StringBuilder("Compilation failed:\n");
                    for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                        errorMsg.append(diagnostic.toString()).append("\n");
                    }
                    throw new ScriptExecutionException(errorMsg.toString());
                }
            }

            // 保存编译产物
            source.setCompiledArtifact(outputDir);
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
                // 使用 JavaCompiler API 编译整个目录
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                if (compiler == null) {
                    // 降级命令行
                    try {
                        ProcessBuilder pb = new ProcessBuilder("javac", "-d", dir.toString());
                        Files.walk(dir).filter(p -> p.toString().endsWith(".java")).forEach(p -> pb.command().add(p.toString()));
                        pb.redirectErrorStream(true);
                        Process process = pb.start();
                        String compileOutput = readStream(process.getInputStream());
                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            throw new ScriptExecutionException(
                                    "Directory compilation failed with exit code " + exitCode +
                                            "\n=== Compiler output ===\n" + compileOutput
                            );
                        }
                    } catch (IOException e) {
                        throw new ScriptExecutionException(
                                "Cannot execute javac. Please ensure JDK is installed and javac is in PATH. Error: " + e.getMessage(), e
                        );
                    }
                } else {
                    // 收集所有 .java 文件
                    List<File> javaFiles = Files.walk(dir)
                            .filter(p -> p.toString().endsWith(".java"))
                            .map(Path::toFile)
                            .collect(Collectors.toList());
                    if (!javaFiles.isEmpty()) {
                        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
                        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles);

                        List<String> options = new ArrayList<>();
                        options.add("-d");
                        options.add(dir.toString());

                        JavaCompiler.CompilationTask task = compiler.getTask(
                                null, fileManager, diagnostics, options, null, compilationUnits
                        );

                        boolean success = task.call();
                        fileManager.close();

                        if (!success) {
                            StringBuilder errorMsg = new StringBuilder("Directory compilation failed:\n");
                            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                                errorMsg.append(diagnostic.toString()).append("\n");
                            }
                            throw new ScriptExecutionException(errorMsg.toString());
                        }
                    }
                }
            }
            source.setCompiledArtifact(dir);
            source.setCompiled(true);

        } else {
            throw new ScriptExecutionException("Unsupported execution mode: " + mode);
        }
    }

    // 如果父类 BaseScriptExecutor 没有 readStream 方法，可以在这里添加
    // 实际上 BaseScriptExecutor 已有，但为了安全，我们在这里声明，以防父类未定义
    // 我们直接使用父类的方法，因为 BaseScriptExecutor 有 protected String readStream(InputStream) 方法
    // 如果确实没有，可以在这里实现，但为了代码简洁，我们假设父类有。
    // 如果报错，请检查 BaseScriptExecutor 中是否有 readStream 方法，如果没有，请添加下面注释的代码。
    /*
    protected String readStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
    */

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

    private String executeJarMethod(String jarPath, String className, String methodName, Map<String, Object> params) throws Exception {
        URL jarUrl = new File(jarPath).toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl})) {
            Class<?> clazz = loader.loadClass(className);
            Method[] methods = clazz.getMethods();
            List<Method> candidates = Arrays.stream(methods)
                    .filter(m -> m.getName().equals(methodName))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                throw new ScriptExecutionException("No method found with name: " + methodName);
            }

            Object argsObj = params.get("args");
            List<Object> argList = new ArrayList<>();
            if (argsObj instanceof Object[]) {
                argList.addAll(Arrays.asList((Object[]) argsObj));
            } else if (argsObj instanceof List) {
                argList.addAll((List<?>) argsObj);
            } else if (argsObj != null) {
                argList.add(argsObj);
            }
            if (argList.isEmpty() && params.containsKey("arg")) {
                argList.add(params.get("arg"));
            }

            Method selectedMethod = null;
            if (candidates.size() == 1) {
                selectedMethod = candidates.get(0);
            } else {
                List<Method> matchedByCount = candidates.stream()
                        .filter(m -> m.getParameterCount() == argList.size())
                        .collect(Collectors.toList());
                if (matchedByCount.isEmpty()) {
                    throw new ScriptExecutionException("No method with " + argList.size() + " parameters found for method: " + methodName);
                }
                if (matchedByCount.size() == 1) {
                    selectedMethod = matchedByCount.get(0);
                } else {
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

            if (selectedMethod == null) {
                throw new ScriptExecutionException("No suitable method found: " + methodName);
            }

            if (selectedMethod.getParameterCount() != argList.size()) {
                throw new ScriptExecutionException(
                        String.format("Parameter count mismatch for method '%s': expected %d, got %d",
                                methodName, selectedMethod.getParameterCount(), argList.size())
                );
            }

            if (!canConvertArguments(argList, selectedMethod.getParameterTypes())) {
                throw new ScriptExecutionException(
                        String.format("Parameter types incompatible for method '%s'. Expected types: %s, provided args: %s",
                                methodName,
                                Arrays.toString(selectedMethod.getParameterTypes()),
                                argList.stream().map(Object::getClass).map(Class::getSimpleName).collect(Collectors.toList())
                        )
                );
            }

            Class<?>[] paramTypes = selectedMethod.getParameterTypes();
            Object[] convertedArgs = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                convertedArgs[i] = convertToType(argList.get(i), paramTypes[i]);
            }

            Object result = selectedMethod.invoke(null, convertedArgs);
            return result != null ? result.toString() : "";
        }
    }

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

    private Object convertToType(Object value, Class<?> targetType) throws ScriptExecutionException {
        if (value == null) {
            if (targetType.isPrimitive()) {
                throw new ScriptExecutionException("Cannot convert null to primitive type: " + targetType);
            }
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        String str = value.toString();
        if (targetType == String.class) {
            return str;
        }
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
        String packageName = "";
        java.util.regex.Pattern pkgPattern = java.util.regex.Pattern.compile("package\\s+([\\w.]+);");
        java.util.regex.Matcher pkgMatcher = pkgPattern.matcher(code);
        if (pkgMatcher.find()) {
            packageName = pkgMatcher.group(1);
        }

        String className = null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)");
        java.util.regex.Matcher m = p.matcher(code);
        if (m.find()) {
            className = m.group(1);
        } else {
            p = java.util.regex.Pattern.compile("class\\s+(\\w+)");
            m = p.matcher(code);
            if (m.find()) {
                className = m.group(1);
            }
        }

        if (className == null) {
            return null;
        }

        return packageName.isEmpty() ? className : packageName + "." + className;
    }
}