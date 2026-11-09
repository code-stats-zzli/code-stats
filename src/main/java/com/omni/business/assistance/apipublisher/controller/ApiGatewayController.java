package com.omni.business.assistance.apipublisher.controller;

import com.omni.business.assistance.apipublisher.service.ApiExecuteService;
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
@RequestMapping("/api/gateway")
public class ApiGatewayController {

    @Autowired
    private ApiExecuteService service;

    @RequestMapping("/execute")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String execute(@RequestBody Map<String, Object> req) {
        try {
            if (req == null) return err("请求体为空");
            String apiId = req.get("apiId") == null ? null : String.valueOf(req.get("apiId"));
            if (apiId == null || apiId.trim().isEmpty()) return err("缺少 apiId");

            Map<String, Object> params;
            Object raw = req.get("params");
            if (raw instanceof Map) {
                params = (Map<String, Object>) raw;
            } else {
                params = Collections.emptyMap();
            }

            Map<String, Object> result = service.execute(apiId.trim(), params);
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            return err(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private String err(String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", false);
        out.put("error", msg);
        return JsonUtil.toJson(out);
    }
}