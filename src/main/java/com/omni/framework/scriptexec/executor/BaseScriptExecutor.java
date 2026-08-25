package com.omni.framework.scriptexec.executor;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import com.omni.framework.scriptexec.exception.ScriptExecutionException;
import com.omni.framework.scriptexec.executor.model.ScriptSource;
import com.omni.framework.util.JsonUtil;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class BaseScriptExecutor implements ScriptExecutor {
    private static final Logger log = LoggerFactory.getLogger(BaseScriptExecutor.class);

    @Override
    public final String execute(String scriptContent, Map<String, Object> params) throws Exception {
        log.info("=== Script execution started ===");
        try {
            preprocessParams(params);
            ScriptSource source = resolveScriptSource(scriptContent, params);
            prepareExecution(source, params);
            Map<String, Object> execParams = parseExecutionParam(params);
            String rawResult = doExecute(source, execParams);
            return postProcess(rawResult, params);
        } catch (Exception e) {
            log.error("Script execution failed", e);
            throw new ScriptExecutionException("Script execution failed: " + e.getMessage(), e);
        }
    }

    protected void preprocessParams(Map<String, Object> params) throws ScriptExecutionException {
        Boolean needCompile = (Boolean) params.getOrDefault("needCompile", false);
        params.put("needCompile", needCompile);
        String mode = (String) params.getOrDefault("executionMode", "source");
        if (!"source".equals(mode) && !"binary".equals(mode) && !"jar".equals(mode) && !"directory".equals(mode)) {
            throw new ScriptExecutionException("Invalid executionMode: " + mode);
        }
        params.put("executionMode", mode);
        if("source".equals(mode))params.put("needCompile", true);
        String content = (String) params.get("scriptContent");
        if (content != null && isBase64(content)) {
            byte[] decoded = java.util.Base64.getDecoder().decode(content);
            params.put("scriptContent", new String(decoded, StandardCharsets.UTF_8));
        }
    }

    private boolean isBase64(String str) {
        return str.matches("^[A-Za-z0-9+/]+=*$");
    }

    protected ScriptSource resolveScriptSource(String scriptContent, Map<String, Object> params) throws Exception {
        ScriptSource source = new ScriptSource();
        String mode = (String) params.get("executionMode");
        String language = (String) params.getOrDefault("language", getDefaultLanguage());
        source.setLanguage(language);
        source.setExecutionMode(mode);

        String pathStr = (String) params.get("scriptPath");
        String content = (String) params.get("scriptContent");

        if ("directory".equals(mode)) {
            if (pathStr == null || pathStr.trim().isEmpty())
                throw new ScriptExecutionException("scriptPath is required for directory mode");
            Path dir = Paths.get(pathStr);
            if (!Files.isDirectory(dir))
                throw new ScriptExecutionException("Path is not a directory: " + pathStr);
            source.setPath(dir);
            source.setCompiled(false);
            return source;
        }

        if (pathStr != null && !pathStr.trim().isEmpty()) {
            Path p = Paths.get(pathStr);
            if (!Files.exists(p))
                throw new ScriptExecutionException("Script path does not exist: " + pathStr);
            source.setPath(p);
            if ("source".equals(mode)) {
                String fileContent = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                source.setContent(fileContent);
            } else {
                source.setCompiled(true);
                source.setCompiledArtifact(p);
            }
        } else {
            if (content == null || content.trim().isEmpty())
                throw new ScriptExecutionException("Neither scriptPath nor scriptContent provided");
            source.setContent(content);
            if ("binary".equals(mode) || "jar".equals(mode))
                source.setCompiled(false);
        }
        return source;
    }

    protected abstract void prepareExecution(ScriptSource source, Map<String, Object> params) throws Exception;
    protected abstract String doExecute(ScriptSource source, Map<String, Object> params) throws Exception;
    protected String postProcess(String rawResult, Map<String, Object> params) {
        return rawResult != null ? rawResult.trim() : "";
    }
    protected abstract String getDefaultLanguage();

    protected Map<String, Object> parseExecutionParam(Map<String, Object> params) {
        //if (params.containsKey("scriptParam")) return (Map<String, Object>) params.get("scriptParam");
        //if (params.containsKey("params")) return (Map<String, Object>) params.get("params");
        return params;
    }

    protected String runProcess(ProcessBuilder pb, long timeoutSeconds) throws Exception {
        return runProcess(pb, timeoutSeconds, null);
    }

    protected String runProcess(ProcessBuilder pb, long timeoutSeconds, String resultFile) throws Exception {
        log.info("Running process: {}", String.join(" ", pb.command()));
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder stdoutBuffer = new StringBuilder();
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[8192];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    if (resultFile == null) {
                        synchronized (stdoutBuffer) { stdoutBuffer.append(buffer, 0, len); }
                    } else if (log.isDebugEnabled()) {
                        log.debug("Process output: {}", new String(buffer, 0, len));
                    }
                }
            } catch (IOException e) {
                log.warn("Error reading process output", e);
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            readerThread.interrupt();
            throw new ScriptExecutionException("Process timeout after " + timeoutSeconds + "s");
        }
        readerThread.join(5000);

        int exitCode = process.exitValue();
        String result;
        if (resultFile != null && !resultFile.isEmpty()) {
            Path filePath = Paths.get(resultFile);
            if (!Files.exists(filePath))
                throw new ScriptExecutionException("Result file not found: " + resultFile);
            result = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } else {
            synchronized (stdoutBuffer) { result = stdoutBuffer.toString(); }
        }

        if (exitCode != 0) {
            String detail = (resultFile != null) ?
                    "Process exited with code " + exitCode + ". See output file: " + resultFile :
                    "Process exited with code " + exitCode + "\nOutput: " + result;
            throw new ScriptExecutionException(detail);
        }
        return result;
    }

    protected Path createTempFile(String content, String suffix) throws Exception {
        Path temp = Files.createTempFile("script_", suffix);
        Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
        return temp;
    }

    protected void addArgs(ProcessBuilder pb, Map<String, Object> params) {
        Object argsObj = params.get("params");
        if(argsObj == null)argsObj = params.get("scriptParam");
        if(argsObj == null)argsObj = params.get("args");
        if(argsObj == null)argsObj = params;
        if (argsObj instanceof String[]) {
            for (String arg : (String[]) argsObj) pb.command().add(arg);
        } else if (argsObj instanceof String) {
            pb.command().add((String) argsObj);
        }else {
            pb.command().add(JsonUtil.toJson(argsObj));
        }
    }

    /**
     * 读取进程输出流（用于捕获编译错误信息）
     */
    public String readStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}