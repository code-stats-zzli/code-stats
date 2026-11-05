package com.omni.business.assistance.autotest.controller;


import com.omni.business.assistance.autotest.model.Project;
import com.omni.business.assistance.autotest.service.ProjectService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.Map;

@Controller
@RequestMapping("/autotest/api/project")
public class ProjectController {
    @Autowired
    private ProjectService projectService;

    @RequestMapping("/projects")
    @ResponseBody
    public String listProjects() {
        return JsonUtil.toJson(projectService.listAll());
    }

    @RequestMapping("/get")
    @ResponseBody
    public String getProject(String id) {
        return JsonUtil.toJson(projectService.getById(id));
    }

    @RequestMapping("/save")
    @ResponseBody
    public String saveProject(String body) {
        Project project = JsonUtil.parseJson(body, Project.class);
        String id = projectService.save(project);
        return JsonUtil.toJson(Map.of("success", true, "id", id));
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String deleteProject(String id) {
        projectService.delete(id);
        return JsonUtil.toJson(Map.of("success", true));
    }
}