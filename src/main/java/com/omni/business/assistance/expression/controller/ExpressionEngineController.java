package com.omni.business.assistance.expression.controller;

import com.omni.business.assistance.expression.service.ExpressionEngineService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/expression/api")
public class ExpressionEngineController {

    @Autowired
    private ExpressionEngineService service;

    @RequestMapping("/functions")
    @ResponseBody
    public String functions() {
        try {
            return JsonUtil.toJson(service.listFunctionMeta());
        } catch (Exception e) {
            return JsonUtil.toJson(err("加载函数列表失败: " + e.getMessage()));
        }
    }

    @RequestMapping("/evaluate")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String evaluate(@RequestBody Map<String, Object> req) {
        if (req == null) return JsonUtil.toJson(err("请求体为空"));
        String expression = req.get("expression") == null ? null : req.get("expression").toString();
        Map<String, Object> variables = Collections.emptyMap();
        Object rawVars = req.get("variables");
        if (rawVars instanceof Map) variables = (Map<String, Object>) rawVars;
        return JsonUtil.toJson(service.evaluate(expression, variables));
    }

    private Map<String, Object> err(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }
}