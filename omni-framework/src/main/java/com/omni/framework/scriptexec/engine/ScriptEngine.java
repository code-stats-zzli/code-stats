package com.omni.framework.scriptexec.engine;


import java.util.Map;

public interface ScriptEngine {
    String execute(Map<String, Object> params) throws Exception;

}