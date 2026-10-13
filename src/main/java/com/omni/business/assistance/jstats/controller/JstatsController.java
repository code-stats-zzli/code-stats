package com.omni.business.assistance.jstats.controller;

import com.omni.business.assistance.jstats.model.JstatsMonitorData;
import com.omni.business.assistance.jstats.service.JstatsMonitorService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/3d/api/jstats")
public class JstatsController {

    @Autowired
    private JstatsMonitorService service;

    @RequestMapping("/monitor")
    @ResponseBody
    public String getMonitorData() {
        Map<String, Object> result = new HashMap<>();
        try {
            HttpServletRequest request = RequestContextHolder.getRequest();
            String pid = request.getParameter("pid"); // 可选参数
            JstatsMonitorData summary = service.collectAllData();
            result.put("success", true);
            result.put("data", summary);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            e.printStackTrace(); // 确保错误输出到控制台
        }
        return JsonUtil.toJson(result);
    }
}