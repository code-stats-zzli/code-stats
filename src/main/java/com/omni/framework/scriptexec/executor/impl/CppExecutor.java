package com.omni.framework.scriptexec.executor.impl;


import com.omni.framework.scriptexec.exception.ScriptExecutionException;
import com.omni.framework.scriptexec.executor.BaseScriptExecutor;
import com.omni.framework.scriptexec.executor.model.ScriptSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class CppExecutor extends BaseScriptExecutor {

    @Override
    protected String getDefaultLanguage() {
        return "cpp";
    }

    @Override
    protected void prepareExecution(ScriptSource source, Map<String, Object> params) throws Exception {
        String mode = source.getExecutionMode();
        if ("source".equals(mode)) {
            boolean needCompile = (Boolean) params.getOrDefault("needCompile", true);
            if (!needCompile) {
                throw new ScriptExecutionException("C++ source mode requires needCompile=true");
            }
            String content;
            if (source.getPath() != null && Files.exists(source.getPath())) {
                content = new String(Files.readAllBytes(source.getPath()));
            } else {
                content = source.getContent();
                if (content == null) throw new ScriptExecutionException("No content for C++ source");
            }
            Path cppFile = createTempFile(content, ".cpp");
            String exeName = cppFile.toString().replace(".cpp", "");
            ProcessBuilder pb = new ProcessBuilder("g++", cppFile.toString(), "-o", exeName);
            pb.redirectErrorStream(true);
            int code = pb.start().waitFor();
            if (code != 0) throw new ScriptExecutionException("g++ compilation failed");
            Files.deleteIfExists(cppFile);
            source.setCompiledArtifact(Paths.get(exeName));
            source.setCompiled(true);
        } else if ("binary".equals(mode)) {
            if (source.getPath() != null && Files.exists(source.getPath())) {
                source.setCompiledArtifact(source.getPath());
                source.setCompiled(true);
                return;
            }
            String content = source.getContent();
            if (content == null) throw new ScriptExecutionException("No content for C++ binary");
            Path binFile = createTempFile(content, ".bin");
            if (!binFile.toFile().setExecutable(true)) {
                throw new ScriptExecutionException("Cannot make binary executable");
            }
            source.setCompiledArtifact(binFile);
            source.setCompiled(true);
        } else {
            throw new ScriptExecutionException("C++ executor does not support mode: " + mode);
        }
    }

    @Override
    protected String doExecute(ScriptSource source, Map<String, Object> params) throws Exception {
        Path exe = source.getCompiledArtifact();
        if (exe == null) throw new ScriptExecutionException("No executable prepared");
        ProcessBuilder pb = new ProcessBuilder(exe.toString());
        Object argsObj = params.get("args");
        if (argsObj instanceof String[]) {
            for (String arg : (String[]) argsObj) pb.command().add(arg);
        }
        return runProcess(pb, 30);
    }
}