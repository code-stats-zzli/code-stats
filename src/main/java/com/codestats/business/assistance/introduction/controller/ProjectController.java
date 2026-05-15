package com.codestats.business.assistance.introduction.controller;

import com.codestats.framework.util.ParamUtils;
import com.codestats.framework.spring.annotation.Controller;
import com.codestats.framework.spring.annotation.RequestMapping;
import com.codestats.framework.spring.annotation.ResponseBody;
import com.codestats.framework.util.JsonUtil;

import java.util.*;

@Controller
@RequestMapping("/api/project")
public class ProjectController {

    @RequestMapping("/nav")
    @ResponseBody
    public String getProjectNav() {
        List<Map<String, Object>> navList = new ArrayList<>();
        navList.add(createNavItem("arch", "🏛️ 项目架构介绍",
                "完整剖析自研框架的设计思想、容器原理、Pipeline 流程、类加载机制等。"));
        navList.add(createNavItem("framework", "⚙️ 轻量 Web 框架",
                "自研 Mini Spring/Tomcat，包含 IoC、MVC、连接池、MyBatis 风格映射。"));
        navList.add(createNavItem("database", "🗄️ 数据库管理",
                "基于 JDBC 元数据服务，支持 MySQL 树形浏览、表结构查看。"));
        navList.add(createNavItem("codestats", "📊 代码分析",
                "多线程代码统计、语言识别、注释排除、复杂度估算，支持 GUI/CLI。"));
        navList.add(createNavItem("files", "📁 目录管理",
                "服务端文件浏览、上传、编辑，基于虚拟文件系统。"));
        navList.add(createNavItem("agent", "🤖 智能 Agent",
                "AI 驱动的代码助手，自然语言查询、代码生成。"));
        return JsonUtil.toJson(navList);
    }

    private Map<String, Object> createNavItem(String id, String title, String summary) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("summary", summary);
        return item;
    }

    @RequestMapping("/detail")
    @ResponseBody
    public String getModuleDetail() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String moduleId = params.get("moduleId");
        if (moduleId == null || moduleId.trim().isEmpty()) {
            moduleId = "arch";
        }
        switch (moduleId) {
            case "arch":
                return buildArchitectureDetail();
            case "framework":
                return buildFrameworkDetail();
            case "database":
                return buildDatabaseDetail();
            case "codestats":
                return buildCodeStatsDetail();
            case "files":
                return buildFilesDetail();
            case "agent":
                return buildAgentDetail();
            default:
                return "<div class='detail-placeholder'><p>该模块详细说明正在建设中...</p></div>";
        }
    }

    // ========== 各模块详细内容构建方法（JDK 1.8 字符串拼接） ==========

    private String buildArchitectureDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"arch-detail\">");
        sb.append("<h2>🏛️ 项目架构全景</h2>");
        sb.append("<p>CodeStats 是一个完全自研的轻量级 Java Web 平台，底层基于迷你 Tomcat 和 IoC 容器，展示了从零构建企业级框架的核心技术。</p>");
        sb.append("<h3>📦 分层架构</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>表现层</strong>：HTML5/CSS3/JS 前端 + WebSocket</li>");
        sb.append("<li><strong>Web 容器层</strong>：自研 Connector（NIO 线程池）、Pipeline-Valve 责任链、WebappClassLoader</li>");
        sb.append("<li><strong>MVC 核心</strong>：DispatcherServlet、HandlerMapping、参数解析、视图解析</li>");
        sb.append("<li><strong>业务层</strong>：Controller / Service / Mapper 注解驱动</li>");
        sb.append("<li><strong>基础设施层</strong>：JDBC 连接池、JdbcTemplate、MyBatis 风格代理、本地缓存（Caffeine）</li>");
        sb.append("</ul>");
        sb.append("<h3>⚙️ 核心流程</h3>");
        sb.append("<p>请求到达 → Connector 解析 → Pipeline 流转 → DispatcherServlet 匹配 Handler → 执行 Controller → 返回 JSON/HTML。</p>");
        sb.append("<h3>🔧 技术亮点</h3>");
        sb.append("<ul>");
        sb.append("<li>完全自研 IoC 容器，支持 @Autowired 注入</li>");
        sb.append("<li>模拟 Tomcat 类加载隔离，支持 Web 应用热部署</li>");
        sb.append("<li>JDBC 连接池：阻塞队列 + Semaphore 限流，支持空闲连接回收</li>");
        sb.append("<li>代码分析引擎：Pipeline 模式，多线程并行处理，支持 GitIgnore 过滤</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildFrameworkDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"framework-detail\">");
        sb.append("<h2>⚙️ 轻量 Web 框架</h2>");
        sb.append("<p>完全自研的 IoC 容器与 MVC 框架，核心代码无任何第三方依赖，实现了 Spring 框架的核心功能。</p>");
        sb.append("<h3>📌 核心特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>IoC 容器</strong>：通过包扫描自动创建 @Controller、@Service、@Repository、@Component 实例，支持 @Autowired 依赖注入。</li>");
        sb.append("<li><strong>MVC 架构</strong>：@RequestMapping 映射 URL，方法参数支持 HttpServletRequest/Response 自动注入，@ResponseBody 自动 JSON 序列化。</li>");
        sb.append("<li><strong>嵌入式 Tomcat</strong>：自研 Connector（基于 BIO/NIO 线程池）、Pipeline-Valve 责任链、Context 隔离的类加载器。</li>");
        sb.append("<li><strong>数据访问</strong>：@Mapper 接口 + @Select 注解动态代理，自动执行 SQL 并映射结果；内置 JdbcTemplate 和连接池。</li>");
        sb.append("</ul>");
        sb.append("<h3>📁 关键类示例</h3>");
        sb.append("<ul>");
        sb.append("<li><code>SimpleApplicationContext</code>：扫描、实例化、依赖注入核心。</li>");
        sb.append("<li><code>DispatcherServlet</code>：请求分发、Handler 匹配、静态资源服务。</li>");
        sb.append("<li><code>MapperProxy</code>：动态代理执行 @Select 方法。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔌 快速体验</h3>");
        sb.append("<p>启动后访问 <a href=\"/demo/user/name\" target=\"_blank\">/demo/user/name</a> 返回用户名，或 <a href=\"/demo/hello/text?name=World\" target=\"_blank\">/demo/hello/text?name=World</a> 测试参数绑定。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildDatabaseDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"database-detail\">");
        sb.append("<h2>🗄️ 数据库管理模块</h2>");
        sb.append("<p>基于 JDBC 元数据 API 实现的轻量级数据库客户端，支持 MySQL 数据库的元数据浏览（数据库、表、列、主键、索引等）。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li>树形导航栏：连接 → 数据库 → 表 → 列/视图/索引，支持动态加载。</li>");
        sb.append("<li>表属性页面：显示表基本信息（引擎、字符集）和列详细信息（类型、可空、自增等）。</li>");
        sb.append("<li>数据视图（预留）：支持分页查询表数据。</li>");
        sb.append("<li>ER 图（预留）：可视化展示表关系。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>DatabaseMetadataService</code>：封装 JDBC DatabaseMetaData，提供 <code>getTables()</code>、<code>getColumns()</code> 等方法。</li>");
        sb.append("<li><code>DataModel</code>：统一的行列数据模型，支持转换为 JSON、HTML 表格、CSV 等格式。</li>");
        sb.append("<li><code>DatabaseClientController</code>：提供 <code>/api/databases</code>、<code>/api/tables</code>、<code>/api/columns</code> 三个 REST 接口，返回 JSON 供前端树形菜单使用。</li>");
        sb.append("<li>前端采用原生 JavaScript 动态构建树，无第三方库。</li>");
        sb.append("</ul>");
        sb.append("<h3>🌐 使用方式</h3>");
        sb.append("<p>在顶部导航点击“数据库”标签，左侧展开连接（localhost），即可浏览数据库结构。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildCodeStatsDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"codestats-detail\">");
        sb.append("<h2>📊 代码分析模块</h2>");
        sb.append("<p>功能完备的代码统计工具，支持多线程分析、多种语言识别、注释排除、复杂度估算，并可生成 JSON/CSV 报告。</p>");
        sb.append("<h3>⚡ 核心功能</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>文件收集</strong>：递归扫描目录，支持扩展名过滤、排除路径、.gitignore 规则。</li>");
        sb.append("<li><strong>并行分析</strong>：可配置线程数，每个文件独立分析行数、代码行、注释行、空行、复杂性（关键字计数）。</li>");
        sb.append("<li><strong>语言识别</strong>：内置 30+ 种语言（Java、Python、JS、Go 等），通过扩展名或 shebang 行识别，支持自定义注释规则。</li>");
        sb.append("<li><strong>结果输出</strong>：支持 JSON / CSV / Console 格式，并可选择将收集的文件内容合并为一个文件（带路径注释）。</li>");
        sb.append("<li><strong>GUI 模式</strong>：提供 Swing 图形界面，可实时预览分析进度。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔍 使用方式</h3>");
        sb.append("<p><strong>Web 模式</strong>：点击顶部“代码分析”标签，填写根目录、扩展名等参数，点击“开始分析”，结果以表格形式展示，支持导出 CSV 和下载合并文件。</p>");
        sb.append("<p><strong>命令行模式</strong>：执行 <code>java -jar codestats.jar -d ./src -e java -o json</code> 等参数。</p>");
        sb.append("<p><strong>GUI 模式</strong>：运行 <code>com.codestats.lib.codestats.gui.CodeStatsGUI</code> 主类。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildFilesDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"files-detail\">");
        sb.append("<h2>📁 目录管理模块</h2>");
        sb.append("<p>提供基于 HTTP 的文件浏览、读取、上传和编辑功能，支持路径安全校验，可作为轻量级文件管理器。</p>");
        sb.append("<h3>📌 功能规划</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>文件列表</strong>：GET <code>/demo/files/api/list?path=/xxx</code> 返回目录下的文件和子目录 JSON。</li>");
        sb.append("<li><strong>文件读取</strong>：GET <code>/demo/files/api/read?path=/xxx/file.txt</code> 返回文件内容。</li>");
        sb.append("<li><strong>文件上传</strong>：POST <code>/demo/files/api/upload</code> 支持 multipart/form-data 上传文件到指定路径。</li>");
        sb.append("<li><strong>编辑保存</strong>：POST <code>/demo/files/api/save</code> 保存编辑后的文本文件。</li>");
        sb.append("<li><strong>安全防护</strong>：路径遍历攻击防范（禁止包含 ..），仅允许操作配置的根目录。</li>");
        sb.append("</ul>");
        sb.append("<h3>💡 技术实现示例</h3>");
        sb.append("<p>后端使用 <code>java.nio.file.Files</code> API，通过 <code>ParamUtils.parseQueryParams()</code> 获取参数，返回 JSON。前端可基于 Fetch API 快速实现文件浏览器。</p>");
        sb.append("<p>详细接口文档将在后续版本提供。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildAgentDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"agent-detail\">");
        sb.append("<h2>🤖 智能 Agent</h2>");
        sb.append("<p>AI 驱动的开发助手，集成大语言模型 API，支持自然语言代码生成、解释、单元测试生成等能力。</p>");
        sb.append("<h3>✨ 目标功能</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>代码生成</strong>：根据自然语言描述生成代码片段（Java/Python/JS 等）。</li>");
        sb.append("<li><strong>代码解释</strong>：选中代码块，Agent 可解释其逻辑和潜在问题。</li>");
        sb.append("<li><strong>单元测试生成</strong>：为指定方法自动生成 JUnit 测试用例。</li>");
        sb.append("<li><strong>对话历史</strong>：支持会话管理，可上下文连续对话。</li>");
        sb.append("<li><strong>WebSocket 实时通信</strong>：支持流式输出，实时显示生成过程。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术架构</h3>");
        sb.append("<ul>");
        sb.append("<li>后端：Java + WebSocket 代理，对接 OpenAI/国产大模型 API。</li>");
        sb.append("<li>前端：基于 WebSocket 的 chat UI，支持 Markdown 渲染和代码高亮。</li>");
        sb.append("<li>预留接口：<code>/demo/agent/api/chat</code> (HTTP) 和 <code>/demo/agent/ws</code> (WebSocket)。</li>");
        sb.append("</ul>");
        sb.append("<h3>📢 当前状态</h3>");
        sb.append("<p>本模块为预留扩展，API 接口已定义，具体实现将根据后续需求接入大模型服务。</p>");
        sb.append("</div>");
        return sb.toString();
    }
}