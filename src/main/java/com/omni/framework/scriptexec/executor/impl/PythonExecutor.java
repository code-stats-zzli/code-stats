package com.omni.framework.scriptexec.executor.impl;

import com.omni.framework.scriptexec.exception.ScriptExecutionException;
import com.omni.framework.scriptexec.executor.BaseScriptExecutor;
import com.omni.framework.scriptexec.executor.model.ScriptSource;
import com.omni.framework.util.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PythonExecutor extends BaseScriptExecutor {

    @Override
    protected String getDefaultLanguage() {
        return "python";
    }

    @Override
    protected void prepareExecution(ScriptSource source, Map<String, Object> params) throws Exception {
        String mode = source.getExecutionMode();
        if ("source".equals(mode)) {
            // 如果已经有路径，且路径存在，直接使用；否则将内容写入临时文件
            if (source.getPath() != null && Files.exists(source.getPath())) {
                source.setCompiledArtifact(source.getPath());
                source.setCompiled(true);
            } else {
                String content = source.getContent();
                if (content == null) {
                    throw new ScriptExecutionException("No content for Python source");
                }
                Path pyFile = createTempFile(content, ".py");
                source.setCompiledArtifact(pyFile);
                source.setCompiled(true);
            }
        } else if ("binary".equals(mode)) {
            // 若有路径，直接使用；否则将内容写入临时可执行文件（Python 二进制不常见，但支持）
            if (source.getPath() != null && Files.exists(source.getPath())) {
                source.setCompiledArtifact(source.getPath());
            } else {
                String content = source.getContent();
                if (content == null) {
                    throw new ScriptExecutionException("No content for Python binary");
                }
                Path binFile = createTempFile(content, ".py"); // 实际上 Python 二进制也是 .py
                source.setCompiledArtifact(binFile);
            }
            source.setCompiled(true);
        } else {
            throw new ScriptExecutionException("Python executor does not support mode: " + mode);
        }
    }

    @Override
    protected String doExecute(ScriptSource source, Map<String, Object> params) throws Exception {
        Path scriptFile = source.getCompiledArtifact();
        if (scriptFile == null) {
            throw new ScriptExecutionException("No script file prepared");
        }
        // 构建命令：python3 <script> [参数JSON]
        ProcessBuilder pb = new ProcessBuilder("python", scriptFile.toString());
        // 将业务参数转为 JSON 作为最后一个参数
        String paramJson = JsonUtil.toJson(params);
        pb.command().add(paramJson);
        return runProcess(pb, 30);
    }
}