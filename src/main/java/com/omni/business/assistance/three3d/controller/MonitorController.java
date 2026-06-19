package com.omni.business.assistance.three3d.controller;

import com.omni.business.assistance.three3d.service.JvmMonitorService;
import com.omni.business.assistance.three3d.service.TestChainService;
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

    @Autowired
    private TestChainService testChainService;   // 新增一个 Service

    @RequestMapping("/test-call-chain")
    @ResponseBody
    public String testCallChain() {
        // 触发一个模拟的调用链，返回任务ID或简单结果
        String taskId = testChainService.executeTestChain();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("taskId", taskId);
        result.put("message", "测试调用链已启动，请观察监控大屏的线程详情");
        return JsonUtil.toJson(result);
    }
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