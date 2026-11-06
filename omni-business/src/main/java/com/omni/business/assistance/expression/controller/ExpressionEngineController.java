package com.omni.business.assistance.expression.controller;

import com.omni.business.assistance.expression.service.ExpressionEngineService;
import com.omni.business.common.util.BaseController;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.Collections;
import java.util.Map;

/** ★ L1-01 */
@Controller
@RequestMapping("/expression/api")
public class ExpressionEngineController extends BaseController {

    @Autowired
    private ExpressionEngineService service;

    @RequestMapping("/functions")
    @ResponseBody
    public String functions() {
        try { return JsonUtil.toJson(service.listFunctionMeta()); }
        catch (Exception e) { return error("加载函数列表失败: " + e.getMessage()); }
    }

    @RequestMapping("/evaluate")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String evaluate(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        String expression = req.get("expression") == null ? null : req.get("expression").toString();
        Map<String, Object> variables = Collections.emptyMap();
        Object rawVars = req.get("variables");
        if (rawVars instanceof Map) variables = (Map<String, Object>) rawVars;
        return JsonUtil.toJson(service.evaluate(expression, variables));
    }
}