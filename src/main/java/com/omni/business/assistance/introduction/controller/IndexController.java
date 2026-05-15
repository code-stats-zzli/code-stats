// 文件路径: com.codestats.business.assistance.introduction.controller.IndexController

package com.omni.business.assistance.introduction.controller;

import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.*;

@Controller
@RequestMapping("/api")
public class IndexController {

    @RequestMapping("/info")
    @ResponseBody
    public String getProjectInfo() {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("title", "CodeStats Platform");
        data.put("subtitle", "综合性开发工具平台 — 集数据库管理、代码统计、目录浏览与智能助手于一体");

        // 模块列表：将「轻量 Web 框架」放在第一位，并增加 detailUrl
        List<Map<String, String>> modules = new ArrayList<>();

        // 1. 轻量 Web 框架（置顶，并提供详细架构介绍页面）
        modules.add(createModule("轻量 Web 框架", "⚙️",
                "自研 Mini Spring/Tomcat 实现，内置 IoC、MVC、连接池、MyBatis 风格映射，支撑整个平台运行。",
                null, "/demo/docs/architecture.html"));

        // 2. 数据库管理
        modules.add(createModule("数据库管理", "🗄️",
                "支持 MySQL 元数据浏览、表结构与列信息查看，树形导航直观操作。", "database", null));

        // 3. 代码分析
        modules.add(createModule("代码分析", "📊",
                "多线程代码行数统计、语言识别、注释率分析，支持 GUI 与命令行模式。", "codestats", null));

        // 4. 目录管理
        modules.add(createModule("目录管理", "📁",
                "服务端文件浏览、编辑与上传管理，基于虚拟文件系统。", "files", null));

        // 5. 智能 Agent
        modules.add(createModule("智能 Agent", "🤖",
                "集成 AI 助手，支持自然语言查询、代码生成与上下文对话。", "agent", null));

        data.put("modules", modules);

        // 技术栈
        List<String> techStack = Arrays.asList(
                "Java 8+", "MySQL", "Swing", "WebSocket", "Python (Agent)", "自研 IoC/ORM"
        );
        data.put("techStack", techStack);

        // 快速链接
        List<Map<String, String>> links = new ArrayList<>();
        links.add(createLink("数据库浏览器", "dbclient.html", "🗄️"));
        links.add(createLink("代码统计 GUI", "../codestats-gui", "📊"));
        links.add(createLink("API 测试", "/demo/user/info", "🔌"));
        data.put("links", links);

        return JsonUtil.toJson(data);
    }

    // 增加 detailUrl 参数
    private Map<String, String> createModule(String name, String icon, String desc, String tab, String detailUrl) {
        Map<String, String> module = new LinkedHashMap<>();
        module.put("name", name);
        module.put("icon", icon);
        module.put("desc", desc);
        if (tab != null) module.put("tab", tab);
        if (detailUrl != null) module.put("detailUrl", detailUrl);
        return module;
    }

    private Map<String, String> createLink(String text, String url, String icon) {
        Map<String, String> link = new LinkedHashMap<>();
        link.put("text", text);
        link.put("url", url);
        link.put("icon", icon);
        return link;
    }
}