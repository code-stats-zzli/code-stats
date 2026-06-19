package com.omni.business.assistance.three3d.controller;

import com.omni.business.assistance.three3d.service.JvmMonitorService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/3d/api")
public class MonitorController {

    @Autowired
    private JvmMonitorService jvmService;

    /**
     * 获取系统实时监控数据（真实数据）
     */
    @RequestMapping("/monitor-data")
    @ResponseBody
    public String getMonitorData() {
        Map<String, Object> result = jvmService.getAggregatedMonitorData();
        return JsonUtil.toJson(result);
    }

    /**
     * 获取当前线程的调用链（调试用）
     */
    @RequestMapping("/trace")
    @ResponseBody
    public String getCurrentTrace() {
        Map<String, Object> data = jvmService.getCurrentThreadMonitorData();
        return JsonUtil.toJson(data);
    }
}