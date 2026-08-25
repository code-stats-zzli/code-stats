package com.omni.business.assistance.scriptengine.service;

import com.omni.framework.scriptexec.engine.ScriptEngine;

import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.Map;

@Service
public class ScriptExecutionService {

    @Autowired
    private ScriptEngine scriptEngine;

    public String execute(Map<String, Object> params) throws Exception {
        return scriptEngine.execute(params);
    }


}