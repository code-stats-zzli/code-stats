package com.omni.business.assistance.systemmonitor.controller;

import com.omni.business.assistance.systemmonitor.engine.SystemMonitorEngine;
import com.omni.business.assistance.systemmonitor.service.SystemMonitorService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.Map;

@Controller
@RequestMapping("/systemmonitor/api")
public class SystemMonitorController {

    @Autowired
    private SystemMonitorService service;

    @Autowired
    private SystemMonitorEngine engine ;

    /** 获取所有组件元数据 */
    @RequestMapping("/components")
    @ResponseBody
    public String components() {
        return JsonUtil.toJson(engine.getComponentsMeta());
    }

    /** 获取所有组件的完整信息 */
    @RequestMapping("/all")
    @ResponseBody
    public String all() {
        return JsonUtil.toJson(service.getAllItemInfo());
    }

    /** 获取指定组件信息 */
    @RequestMapping("/item")
    @ResponseBody
    public String item(String id) {
        if (id == null || id.isEmpty()) {
            return JsonUtil.toJson(Map.of("success", false, "error", "缺少 id 参数"));
        }
        return JsonUtil.toJson(service.getItemInfo(id));
    }

    /** 按分类过滤 */
    @RequestMapping("/category")
    @ResponseBody
    public String byCategory(String category) {
        return JsonUtil.toJson(service.getByCategory(category));
    }

    /** 概览摘要 */
    @RequestMapping("/summary")
    @ResponseBody
    public String summary() {
        return JsonUtil.toJson(service.summary());
    }
}