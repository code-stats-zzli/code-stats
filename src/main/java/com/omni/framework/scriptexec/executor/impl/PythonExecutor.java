package com.omni.framework.scriptexec.executor.impl;

import com.omni.framework.scriptexec.exception.ScriptExecutionException;
import com.omni.framework.scriptexec.executor.BaseScriptExecutor;
import com.omni.framework.scriptexec.executor.model.ScriptSource;
import com.omni.framework.spring.annotation.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class PythonExecutor extends BaseScriptExecutor {

    @Override
    protected String getDefaultLanguage() {
        return "python";
    }

    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    protected void prepareExecution(ScriptSource source, Map<String, Object> params) throws Exception {
        String mode = source.getExecutionMode();
        if ("source".equals(mode)) {
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
            if (source.getPath() != null && Files.exists(source.getPath())) {
                source.setCompiledArtifact(source.getPath());
            } else {
                String content = source.getContent();
                if (content == null) {
                    throw new ScriptExecutionException("No content for Python binary");
                }
                Path binFile = createTempFile(content, ".py");
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
        ProcessBuilder pb = new ProcessBuilder("python", scriptFile.toString());
        addArgs(pb, params);
        return runProcess(pb, 30);
    }
}