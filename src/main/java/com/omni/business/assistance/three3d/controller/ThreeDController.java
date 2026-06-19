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

    // ==================== 保留柱状图接口 ====================
    @RequestMapping("/bar-data")
    @ResponseBody
    public String getBarData() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        params.putIfAbsent("rootDir", System.getProperty("user.dir"));
        params.putIfAbsent("extensions", "java");
        params.putIfAbsent("excludeComments", "true");
        params.putIfAbsent("threadCount", String.valueOf(Runtime.getRuntime().availableProcessors()));
        Map<String, Object> barData = threeDGraphService.getBarData(params);
        return JsonUtil.toJson(barData);
    }

    // ==================== 新增：模块信息接口 ====================
    /**
     * 获取项目模块列表（用于 3D 展示）
     * GET /3d/api/modules
     */
    @RequestMapping("/modules")
    @ResponseBody
    public String getModules() {
        // 直接调用项目介绍接口获取模块列表（复用现有数据）
        // 为避免循环依赖，通过 HTTP 调用或直接构造，这里直接构造静态数据（也可从数据库读取）
        List<Map<String, Object>> modules = new ArrayList<>();
        modules.add(createModule("轻量 Web 框架", "⚙️", "自研 IoC/MVC，嵌入式 Tomcat，连接池，MyBatis 风格映射"));
        modules.add(createModule("数据库管理", "🗄️", "JDBC 元数据服务，树形导航，表结构查看，数据预览"));
        modules.add(createModule("代码分析", "📊", "多线程行数统计，语言识别，注释排除，复杂度估算"));
        modules.add(createModule("目录管理", "📁", "虚拟文件系统，上传下载，在线编辑，安全路径校验"));
        modules.add(createModule("智能 Agent", "🤖", "集成 Ollama/DeepSeek，项目助手，知识库问答"));
        modules.add(createModule("API 调试", "🔌", "Swagger 风格文档，在线请求调试，自定义请求"));
        return JsonUtil.toJson(modules);
    }

    private Map<String, Object> createModule(String name, String icon, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("icon", icon);
        m.put("description", desc);
        return m;
    }
}