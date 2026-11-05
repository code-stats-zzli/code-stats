package com.omni.business.assistance.autotest.controller;


import com.omni.business.assistance.autotest.model.TestExecution;
import com.omni.business.assistance.autotest.service.TestExecutionService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.Map;

@Controller
@RequestMapping("/autotest/api/execute")
public class TestExecutionController {
    @Autowired
    private TestExecutionService executionService;

    @RequestMapping("/execute")
    @ResponseBody
    public String execute(String body) throws Exception {
        Map<String, Object> req = JsonUtil.parseJson(body);
        String caseId = (String) req.get("caseId");
        String triggerUser = (String) req.getOrDefault("triggerUser", "admin");
        TestExecution exec = executionService.execute(caseId, triggerUser);
        return JsonUtil.toJson(Map.of(
                "executionId", exec.getId(),
                "status", exec.getStatus(),
                "passRate", exec.getPassRate()
        ));
    }

    @RequestMapping("/executions")
    @ResponseBody
    public String listExecutions(String caseId) {
        return JsonUtil.toJson(executionService.listByCase(caseId));
    }
}