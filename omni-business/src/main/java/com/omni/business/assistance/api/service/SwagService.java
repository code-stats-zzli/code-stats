package com.omni.business.assistance.api.service;

import com.omni.framework.spring.annotation.Service;
import com.omni.framework.swag.SwagHandler;
import com.omni.framework.swag.core.ApiDefinition;

import java.util.List;

/**
 * API 元数据业务服务
 * 负责调用核心解析器 SwagHandler，并提供业务层面的模块名称定制、权限校验等扩展功能
 */
@Service
public class SwagService {

    /**
     * 获取模块化的 API 定义（业务入口）
     * 可在此处增加：根据用户角色过滤 API、动态修改模块名称、添加额外描述等
     */
    public List<SwagHandler.ModuleApiResult> getModuleApis() throws Exception {
        // 1. 调用核心解析器获取原始数据
        List<SwagHandler.ModuleApiResult> modules = SwagHandler.getModuleApis();

        // 2. 业务层处理：例如根据配置覆盖模块名称、过滤敏感 API、添加额外的模块描述等
        // 示例：为某些模块添加额外的图标或描述（可根据前端需求定制）
        for (SwagHandler.ModuleApiResult module : modules) {
            // 可以对 moduleName 做二次映射（例如从配置文件中读取显示名称）
            // 也可以对 API 列表进行过滤、排序等
            module.getApis().sort((a, b) -> a.getPath().compareTo(b.getPath()));
        }

        return modules;
    }

    /**
     * 获取所有 API 定义（不分组），供其他业务使用
     */
    public List<ApiDefinition> getAllApis() throws Exception {
        List<SwagHandler.ModuleApiResult> modules = getModuleApis();
        return modules.stream()
                .flatMap(m -> m.getApis().stream())
                .collect(java.util.stream.Collectors.toList());
    }
}