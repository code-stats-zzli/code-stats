package com.omni.business.assistance.three3d.controller;

import com.omni.business.assistance.three3d.service.ThreeDGraphService;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.*;

@Controller
@RequestMapping("/3d/api")
public class ThreeDController {
    private static final Logger log = LoggerFactory.getLogger(ThreeDController.class);

    @Autowired
    private ThreeDGraphService threeDGraphService;

    @RequestMapping("/config")
    @ResponseBody
    public String getSceneConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("color", "#0066ff");
        config.put("size", 2.0);
        config.put("rotationSpeed", 0.01);
        config.put("wireframe", false);
        return JsonUtil.toJson(config);
    }

    @RequestMapping("/models")
    @ResponseBody
    public String getModelList() {
        List<Map<String, String>> models = new ArrayList<>();
        models.add(Map.of("id", "cube", "name", "立方体"));
        models.add(Map.of("id", "sphere", "name", "球体"));
        models.add(Map.of("id", "torus", "name", "圆环"));
        return JsonUtil.toJson(models);
    }

    /**
     * 获取 3D 依赖图数据（支持按类名过滤）
     * 参数：rootDir, extensions, excludePaths, extraPaths, threadCount, excludeComments, className(可选)
     */
    @RequestMapping("/graph")
    @ResponseBody
    public String getGraphData() {
        Map<String, String> params = ParamUtils.parseQueryParams();

        // 设置默认参数
        params.putIfAbsent("rootDir", System.getProperty("user.dir"));
        params.putIfAbsent("extensions", "java");
        params.putIfAbsent("excludeComments", "true");
        params.putIfAbsent("threadCount", String.valueOf(Runtime.getRuntime().availableProcessors()));

        Map<String, Object> graphData = threeDGraphService.getGraphData(params);
        return JsonUtil.toJson(graphData);
    }

    /**
     * 获取所有类名列表（用于前端下拉框）
     */
    @RequestMapping("/classes")
    @ResponseBody
    public String getClassNames() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        params.putIfAbsent("rootDir", System.getProperty("user.dir"));
        params.putIfAbsent("extensions", "java");
        params.putIfAbsent("excludeComments", "true");
        params.putIfAbsent("threadCount", String.valueOf(Runtime.getRuntime().availableProcessors()));

        List<String> classNames = threeDGraphService.getClassNames(params);
        return JsonUtil.toJson(classNames);
    }
}