package com.omni.business.assistance.autotest.controller;

import com.omni.autotest.core.model.TestCase;
import com.omni.business.assistance.autotest.model.TestAssertion;
import com.omni.business.assistance.autotest.model.TestStep;
import com.omni.business.assistance.autotest.service.TestCaseService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.Map;

@Controller
@RequestMapping("/autotest/api/case")
public class TestCaseController {
    @Autowired
    private TestCaseService testCaseService;

    @RequestMapping("/cases")
    @ResponseBody
    public String listCases(String projectId) {
        return JsonUtil.toJson(testCaseService.listByProject(projectId));
    }

    @RequestMapping("/get")
    @ResponseBody
    public String getCase(String id) {
        return JsonUtil.toJson(testCaseService.getById(id));
    }

    @RequestMapping("/save")
    @ResponseBody
    public String saveCase(String body) {
        TestCase tc = JsonUtil.parseJson(body, TestCase.class);
        String id = testCaseService.save(tc);
        return JsonUtil.toJson(Map.of("success", true, "id", id));
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String deleteCase(String id) {
        testCaseService.delete(id);
        return JsonUtil.toJson(Map.of("success", true));
    }

    // 步骤操作（可扩展）
    @RequestMapping("/step/save")
    @ResponseBody
    public String saveStep(String body) {
        TestStep step = JsonUtil.parseJson(body, TestStep.class);
        testCaseService.saveStep(step);
        return JsonUtil.toJson(Map.of("success", true));
    }

    @RequestMapping("/step/delete")
    @ResponseBody
    public String deleteStep(String id) {
        testCaseService.deleteStep(id);
        return JsonUtil.toJson(Map.of("success", true));
    }

    // 断言操作
    @RequestMapping("/assertion/save")
    @ResponseBody
    public String saveAssertion(String body) {
        TestAssertion assertion = JsonUtil.parseJson(body, TestAssertion.class);
        testCaseService.saveAssertion(assertion);
        return JsonUtil.toJson(Map.of("success", true));
    }

    @RequestMapping("/assertion/delete")
    @ResponseBody
    public String deleteAssertion(String id) {
        testCaseService.deleteAssertion(id);
        return JsonUtil.toJson(Map.of("success", true));
    }
}