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

    /**
     * 获取场景配置（颜色、大小、旋转速度）
     */
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

    /**
     * 获取预置模型列表（供前端选择或扩展）
     */
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
     * 获取 3D 依赖图数据（节点和边）
     * 调用 ThreeDGraphService 进行数据转换
     *
     * 修改说明：去掉 @RequestParam 参数，改用 ParamUtils.parseQueryParams()
     * 直接获取请求中的所有查询参数，避免参数绑定异常
     */
    @RequestMapping("/graph")
    @ResponseBody
    public String getGraphData() {
        // 从当前请求中解析所有查询参数
        Map<String, String> params = ParamUtils.parseQueryParams();

        // 设置合理的默认参数（如果前端未传）
        if (!params.containsKey("rootDir")) {
            params.put("rootDir", System.getProperty("user.dir"));
        }
        if (!params.containsKey("extensions")) {
            params.put("extensions", "java");
        }
        if (!params.containsKey("excludeComments")) {
            params.put("excludeComments", "true");
        }
        if (!params.containsKey("threadCount")) {
            params.put("threadCount", String.valueOf(Runtime.getRuntime().availableProcessors()));
        }

        Map<String, Object> graphData = threeDGraphService.getGraphData(params);
        return JsonUtil.toJson(graphData);
    }
}