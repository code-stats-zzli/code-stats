package com.omni.framework.scriptexec.executor;

public interface ScriptExecutor {
    String execute(String scriptContent, java.util.Map<String, Object> params) throws Exception;

    /**
     * ★ 新增：返回语言名（"python"/"java"/"c"/"cpp"/"rest"）。
     * Registry 通过此方法自动注册。
     */
    String getLanguage();
}