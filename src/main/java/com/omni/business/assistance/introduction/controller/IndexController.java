// 文件路径: com.omni.business.assistance.introduction.controller.IndexController
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
        data.put("subtitle", "综合性自研 Java Web 平台 — 集轻量框架、数据库管理、代码分析、目录管理、AI Agent、3D 可视化、自动化测试于一体");
        data.put("projectUrl", "https://github.com/omni-framework/codestats");

        // 完整模块列表（与顶部导航及实际功能页签一一对应）
        List<Map<String, String>> modules = new ArrayList<>();

        // 1. 轻量 Web 框架（置顶，详细架构介绍）
        modules.add(createModule("轻量 Web 框架", "⚙️",
                "自研 Mini Spring/Tomcat 实现，内置 IoC、MVC、连接池、MyBatis 风格映射、日志、表达式引擎、缓存，支撑整个平台运行。",
                null, "/demo/docs/architecture.html"));

        // 2. 数据库管理
        modules.add(createModule("数据库管理", "🗄️",
                "支持多数据源管理、MySQL 元数据浏览、表结构与列信息查看、数据预览、SQL 查询、ER 视图拖拽建模。",
                "database", null));

        // 3. 代码分析
        modules.add(createModule("代码分析", "📊",
                "多线程代码行数统计、语言识别、注释率分析、类依赖图、方法级调用链、3D 依赖可视化，支持 GUI 与命令行模式。",
                "codestats", null));

        // 4. 目录管理
        modules.add(createModule("目录管理", "📁",
                "服务端文件浏览、在线编辑、上传下载、ZIP 打包，支持本地与服务器双根目录及路径安全校验。",
                "files", null));

        // 5. 智能 Agent
        modules.add(createModule("智能 Agent", "🤖",
                "集成 Ollama / DeepSeek API / 自定义 API / 内置项目助手，支持自然语言对话、知识库问答与上下文管理。",
                "agent", null));

        // 6. 开源工具
        modules.add(createModule("开源工具", "🧰",
                "DeepSeek 官网智能对话代理，支持连续上下文、多轮问答，通过后端安全转发 API 请求。",
                "opensource", null));

        // 7. API 调试
        modules.add(createModule("API 调试", "🔌",
                "Swagger 风格接口文档自动生成、在线参数填充与请求调试、自定义 HTTP 请求构建器。",
                "apiexplorer", null));

        // 8. 3D 依赖图
        modules.add(createModule("3D 依赖图", "🌐",
                "项目模块 3D 拓扑、3D 柱状图、调用链 3D 拓扑、实时 JVM 监控、系统监控、方法级调用链时序图。",
                "3d", null));

        // 9. 脚本引擎
        modules.add(createModule("脚本引擎", "⚙️",
                "支持 Python / Java / C / C++ / REST 多语言脚本在线编辑与执行，源码编译、参数传递、结果捕获。",
                "scriptengine", null));

        // 10. 自动化测试
        modules.add(createModule("自动化测试", "⚡",
                "项目管理、用例管理、执行中心、报告中心，内置测试步骤引擎、断言处理器与执行结果快照。",
                "autotest", null));

        // 11. 页面搭建
        modules.add(createModule("页面搭建", "🧱",
                "可视化拖拽页面搭建器，支持基础元素与组件元素、属性编辑、HTML/JSON 导入导出、实时预览。",
                "builder", null));

        // 12. 系统监控
        modules.add(createModule("系统监控", "🖥️",
                "CPU、内存、磁盘、GPU、声卡、进程、线程、Java 运行时、文件系统等系统组件实时监控与详情查看。",
                "3d#systemmonitor", null));

        // 13. 题库分析
        modules.add(createModule("题库分析", "📚",
                "Excel/TXT 题库文件扫描合并、关键词搜索、数据预览分页、Excel 导出、屏幕 OCR 识别与题目匹配。",
                "codestats#questionbank", null));

        // 14. 网页爬虫
        modules.add(createModule("网页爬虫", "🕷️",
                "CSDN 博客文章批量下载、并发控制、失败链接记录、文章合并打包、作者信息与统计数据采集。",
                "codestats#webspider", null));

        data.put("modules", modules);

        // 技术栈
        List<String> techStack = Arrays.asList(
                "Java 8+", "MySQL", "Swing", "WebSocket", "Python (Agent)",
                "自研 IoC/ORM", "Three.js", "ECharts", "Playwright", "Tesseract OCR"
        );
        data.put("techStack", techStack);

        // 快速链接
        List<Map<String, String>> links = new ArrayList<>();
        links.add(createLink("数据库浏览器", "db/dbclient.html", "🗄️"));
        links.add(createLink("代码统计 GUI", "../codestats-gui", "📊"));
        links.add(createLink("API 测试", "/demo/user/info", "🔌"));
        links.add(createLink("GitHub 仓库", "https://github.com/omni-framework/codestats", "🐙"));
        data.put("links", links);

        return JsonUtil.toJson(data);
    }

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