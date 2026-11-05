package com.omni.business.assistance.api.controller;

import com.omni.business.assistance.api.service.SwagService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

@Controller
@RequestMapping("/apiexplorer/api")
public class ApiExplorerController {

    @Autowired
    private SwagService swagService;

    /**
     * 获取模块化的 API 定义
     * GET /apiexplorer/api/modules
     */
    @RequestMapping("/modules")
    @ResponseBody
    public String getModuleApis() {
        try {
            return JsonUtil.toJson(swagService.getModuleApis());
        } catch (Exception e) {
            return JsonUtil.toJson(new com.omni.framework.swag.core.ApiDefinition());
        }
    }
}