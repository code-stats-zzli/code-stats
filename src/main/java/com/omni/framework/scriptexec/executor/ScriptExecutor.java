package com.omni.framework.scriptexec.executor;

public interface ScriptExecutor {
    String execute(String scriptContent, java.util.Map<String, Object> params) throws Exception;
}