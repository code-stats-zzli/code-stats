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

    // ==================== 柱状图接口 ====================
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

    // ==================== 模块信息接口 ====================
    @RequestMapping("/modules")
    @ResponseBody
    public String getModules() {
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

    // ==================== 项目调用链数据接口 ====================
    @RequestMapping("/call-chain")
    @ResponseBody
    public String getCallChain() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        params.putIfAbsent("rootDir", System.getProperty("user.dir"));
        params.putIfAbsent("extensions", "java");
        params.putIfAbsent("excludeComments", "true");
        params.putIfAbsent("threadCount", String.valueOf(Runtime.getRuntime().availableProcessors()));
        try {
            Map<String, Object> data = threeDGraphService.getCallChainData(params);
            return JsonUtil.toJson(data);
        } catch (Exception e) {
            log.error("获取调用链数据失败", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    // ==================== 新增：内置系统全链路数据接口 ====================
    @RequestMapping("/system-call-chain")
    @ResponseBody
    public String getSystemCallChain() {
        try {
            Map<String, Object> data = threeDGraphService.getSystemCallChainData();
            return JsonUtil.toJson(data);
        } catch (Exception e) {
            log.error("获取系统全链路数据失败", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    // ==================== 新增：动态调用链追踪（模拟） ====================
    /**
     * 获取模拟的调用链追踪数据（用于流光动画）
     * GET /3d/api/trace
     * 返回示例：包含节点、边、路径顺序、总耗时
     */
    @RequestMapping("/trace")
    @ResponseBody
    public String getTrace() {
        // 模拟调用链（按顺序）
        List<String> tracePath = Arrays.asList(
                "Bootstrap",
                "SpringApplication",
                "Catalina",
                "Connector",
                "Request",
                "Response",
                "DispatcherServlet",
                "HandlerMapping",
                "ParamBinder",
                "AgentController",
                "AIChatService",
                "JdbcTemplate",
                "DataSourceUtils",
                "SimpleDataSource",
                "PooledConnection"
        );

        // 节点定义（名称、类型、颜色）
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, String> colorMap = new HashMap<>();
        colorMap.put("Bootstrap", "#4da6ff");
        colorMap.put("SpringApplication", "#4da6ff");
        colorMap.put("Catalina", "#4da6ff");
        colorMap.put("Connector", "#4da6ff");
        colorMap.put("Request", "#4da6ff");
        colorMap.put("Response", "#4da6ff");
        colorMap.put("DispatcherServlet", "#4da6ff");
        colorMap.put("HandlerMapping", "#4da6ff");
        colorMap.put("ParamBinder", "#4da6ff");
        colorMap.put("AgentController", "#22c55e");
        colorMap.put("AIChatService", "#22c55e");
        colorMap.put("JdbcTemplate", "#f59e0b");
        colorMap.put("DataSourceUtils", "#f59e0b");
        colorMap.put("SimpleDataSource", "#f59e0b");
        colorMap.put("PooledConnection", "#f59e0b");

        // 计算节点位置（分层：前5个在y=4，中间5个在y=0，最后5个在y=-4，水平均匀分布）
        int total = tracePath.size();
        double spacingX = 3.0;
        for (int i = 0; i < total; i++) {
            String name = tracePath.get(i);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", name);
            node.put("label", name);
            // 根据索引分配层级
            double y;
            if (i < 5) y = 4;
            else if (i < 10) y = 0;
            else y = -4;
            // 水平位置居中
            double x = (i - (total - 1) / 2.0) * spacingX;
            double z = 0;
            node.put("x", x);
            node.put("y", y);
            node.put("z", z);
            node.put("color", colorMap.getOrDefault(name, "#94a3b8"));
            node.put("type", i < 5 ? "controller" : (i < 10 ? "service" : "mapper"));
            nodes.add(node);
        }

        // 边：按顺序连接相邻节点
        List<Map<String, String>> links = new ArrayList<>();
        for (int i = 0; i < total - 1; i++) {
            Map<String, String> link = new HashMap<>();
            link.put("source", tracePath.get(i));
            link.put("target", tracePath.get(i + 1));
            links.add(link);
        }

        // 随机生成总耗时（100-500ms），用于粒子速度模拟
        long durationMs = 100 + (long)(Math.random() * 400);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("links", links);
        result.put("tracePath", tracePath);
        result.put("durationMs", durationMs);
        return JsonUtil.toJson(result);
    }
}