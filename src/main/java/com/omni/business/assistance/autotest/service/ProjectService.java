package com.omni.business.assistance.autotest.service;


import com.omni.business.assistance.autotest.mapper.ProjectMapper;
import com.omni.business.assistance.autotest.model.Project;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    @Autowired
    private ProjectMapper projectMapper;

    public Project getById(String id) {
        return projectMapper.selectById(id);
    }

    public List<Project> listAll() {
        return projectMapper.selectAll();
    }

    public String save(Project project) {
        if (project.getId() == null || project.getId().isEmpty()) {
            project.setId(UUID.randomUUID().toString());
            projectMapper.insert(project);
        } else {
            projectMapper.update(project);
        }
        return project.getId();
    }

    public void delete(String id) {
        projectMapper.delete(id);
    }
}