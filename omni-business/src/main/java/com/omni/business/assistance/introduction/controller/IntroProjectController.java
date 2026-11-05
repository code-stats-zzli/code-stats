package com.omni.business.assistance.introduction.controller;

import com.omni.framework.util.ParamUtils;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.*;

@Controller
@RequestMapping("/api/project")
public class IntroProjectController {

    private static final String PROJECT_URL = "https://github.com/omni-framework/codestats";

    @RequestMapping("/nav")
    @ResponseBody
    public String getProjectNav() {
        List<Map<String, Object>> navList = new ArrayList<>();
        navList.add(createNavItem("arch", "🏛️ 项目架构介绍",
                "完整剖析自研框架的设计思想、容器原理、Pipeline 流程、类加载机制、模块划分与整体技术栈。"));
        navList.add(createNavItem("framework", "⚙️ 轻量 Web 框架",
                "自研 Mini Spring/Tomcat，包含 IoC、MVC、连接池、MyBatis 风格映射、日志、表达式引擎、缓存。"));
        navList.add(createNavItem("database", "🗄️ 数据库管理",
                "多数据源管理、元数据浏览、表结构查看、数据预览、SQL 查询、ER 视图拖拽建模。"));
        navList.add(createNavItem("codestats", "📊 代码分析",
                "多线程行数统计、语言识别、注释排除、复杂度估算、类依赖图、方法级调用链、3D 可视化。"));
        navList.add(createNavItem("files", "📁 目录管理",
                "服务端文件浏览、上传下载、在线编辑、ZIP 打包，支持本地与服务器双根目录。"));
        navList.add(createNavItem("agent", "🤖 智能 Agent",
                "AI 驱动的代码助手，支持 Ollama / DeepSeek / 自定义 API / 项目助手，自然语言问答。"));
        navList.add(createNavItem("opensource", "🧰 开源工具",
                "DeepSeek 官网对话代理，支持连续上下文、多轮问答，通过后端安全转发请求。"));
        navList.add(createNavItem("apiexplorer", "🔌 API 调试",
                "Swagger 风格接口文档、在线参数填充、请求调试、自定义 HTTP 请求构建。"));
        navList.add(createNavItem("three3d", "🌐 3D 依赖图",
                "项目模块 3D 拓扑、3D 柱状图、调用链 3D 拓扑、实时 JVM 监控、系统监控、方法级调用链。"));
        navList.add(createNavItem("scriptengine", "⚙️ 脚本引擎",
                "Python / Java / C / C++ / REST 多语言在线编辑与执行，源码编译、参数传递、结果捕获。"));
        navList.add(createNavItem("autotest", "⚡ 自动化测试",
                "项目管理、用例管理、执行中心、报告中心，内置测试步骤引擎、断言处理器与执行快照。"));
        navList.add(createNavItem("builder", "🧱 页面搭建",
                "可视化拖拽页面搭建器，基础元素与组件元素、属性编辑、HTML/JSON 导入导出、实时预览。"));
        navList.add(createNavItem("systemmonitor", "🖥️ 系统监控",
                "CPU、内存、磁盘、GPU、声卡、进程、线程、Java 运行时等系统组件实时监控与详情查看。"));
        navList.add(createNavItem("questionbank", "📚 题库分析",
                "Excel/TXT 题库合并、关键词搜索、数据预览、Excel 导出、屏幕 OCR 识别与题目匹配。"));
        navList.add(createNavItem("spider", "🕷️ 网页爬虫",
                "CSDN 博客批量下载、并发控制、失败链接记录、文章合并打包、作者信息采集。"));
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
            case "arch": return buildArchitectureDetail();
            case "framework": return buildFrameworkDetail();
            case "database": return buildDatabaseDetail();
            case "codestats": return buildCodeStatsDetail();
            case "files": return buildFilesDetail();
            case "agent": return buildAgentDetail();
            case "opensource": return buildOpenSourceDetail();
            case "apiexplorer": return buildApiExplorerDetail();
            case "three3d": return buildThree3DDetail();
            case "scriptengine": return buildScriptEngineDetail();
            case "autotest": return buildAutoTestDetail();
            case "builder": return buildBuilderDetail();
            case "systemmonitor": return buildSystemMonitorDetail();
            case "questionbank": return buildQuestionBankDetail();
            case "spider": return buildSpiderDetail();
            default:
                return "<div class='detail-placeholder'><p>该模块详细说明正在建设中...</p></div>";
        }
    }

    // ========== 各模块详细内容（JDK 1.8 字符串拼接） ==========

    private String buildArchitectureDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"arch-detail\">");
        sb.append("<h2>🏛️ 项目架构全景</h2>");
        sb.append("<p>CodeStats 是一个完全自研的轻量级 Java Web 平台，底层基于迷你 Tomcat 和 IoC 容器，展示了从零构建企业级框架的核心技术。</p>");
        sb.append("<p><strong>项目地址：</strong><a href=\"").append(PROJECT_URL).append("\" target=\"_blank\">").append(PROJECT_URL).append("</a></p>");
        sb.append("<h3>📦 分层架构</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>表现层</strong>：HTML5/CSS3/JS 前端 + WebSocket + SimpleVue 组件化</li>");
        sb.append("<li><strong>Web 容器层</strong>：自研 Connector（NIO 线程池）、Pipeline-Valve 责任链、WebappClassLoader</li>");
        sb.append("<li><strong>MVC 核心</strong>：DispatcherServlet、HandlerMapping、参数解析、视图解析</li>");
        sb.append("<li><strong>业务层</strong>：Controller / Service / Mapper 注解驱动</li>");
        sb.append("<li><strong>基础设施层</strong>：JDBC 连接池、JdbcTemplate、MyBatis 风格代理、本地缓存、日志框架、表达式引擎</li>");
        sb.append("<li><strong>扩展模块</strong>：代码分析、3D 可视化、脚本引擎、自动化测试、页面搭建、系统监控</li>");
        sb.append("</ul>");
        sb.append("<h3>⚙️ 核心流程</h3>");
        sb.append("<p>请求到达 → Connector 解析 → Pipeline 流转 → DispatcherServlet 匹配 Handler → 执行 Controller → 返回 JSON/HTML。</p>");
        sb.append("<h3>🔧 技术亮点</h3>");
        sb.append("<ul>");
        sb.append("<li>完全自研 IoC 容器，支持 @Autowired 注入与 @Primary 优先级</li>");
        sb.append("<li>模拟 Tomcat 类加载隔离，支持 Web 应用热部署</li>");
        sb.append("<li>JDBC 连接池：阻塞队列 + Semaphore 限流，支持空闲连接回收与验证查询</li>");
        sb.append("<li>代码分析引擎：Pipeline 模式，多线程并行处理，支持 GitIgnore 过滤与依赖图构建</li>");
        sb.append("<li>方法级调用链：手写 Java 词法分析器，零第三方依赖，支持 PlantUML 时序图导出</li>");
        sb.append("<li>3D 可视化：Three.js + ECharts GL，支持模块拓扑、依赖图、实时监控大屏</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildFrameworkDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"framework-detail\">");
        sb.append("<h2>⚙️ 轻量 Web 框架</h2>");
        sb.append("<p>完全自研的 IoC 容器与 MVC 框架，核心代码无任何第三方依赖，实现了 Spring 框架的核心功能。</p>");
        sb.append("<p><strong>项目地址：</strong><a href=\"").append(PROJECT_URL).append("\" target=\"_blank\">").append(PROJECT_URL).append("</a></p>");
        sb.append("<h3>📌 核心特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>IoC 容器</strong>：通过包扫描自动创建 @Controller、@Service、@Repository、@Component 实例，支持 @Autowired 依赖注入、@Primary、@Qualifier。</li>");
        sb.append("<li><strong>MVC 架构</strong>：@RequestMapping 映射 URL，方法参数支持 HttpServletRequest/Response 自动注入，@ResponseBody 自动 JSON 序列化，@PathVariable / @RequestParam / @RequestBody 参数绑定。</li>");
        sb.append("<li><strong>嵌入式 Tomcat</strong>：自研 Connector（基于 BIO/NIO 线程池）、Pipeline-Valve 责任链、Context 隔离的类加载器，支持静态资源服务与 404/500 错误页。</li>");
        sb.append("<li><strong>数据访问</strong>：@Mapper 接口 + @Select 注解动态代理，自动执行 SQL 并映射结果；内置 JdbcTemplate、连接池、动态 SQL 执行器。</li>");
        sb.append("<li><strong>日志框架</strong>：自研 LoggerFactory，支持 Console/File/RollingFile/Async Appender，PatternLayout 布局，LevelRangeFilter 过滤。</li>");
        sb.append("<li><strong>表达式引擎</strong>：支持变量、函数、属性访问、数组、三元表达式、赋值，内置 Math/String 函数，支持自定义 Resolver 链。</li>");
        sb.append("<li><strong>缓存抽象</strong>：Cache 接口 + LocalCache（ConcurrentHashMap + TTL）+ Caffeine 可选实现，CacheManager 统一管理。</li>");
        sb.append("</ul>");
        sb.append("<h3>📁 关键类示例</h3>");
        sb.append("<ul>");
        sb.append("<li><code>SimpleApplicationContext</code>：扫描、实例化、依赖注入核心。</li>");
        sb.append("<li><code>DispatcherServlet</code>：请求分发、Handler 匹配、静态资源服务。</li>");
        sb.append("<li><code>MapperProxy</code>：动态代理执行 @Select 方法。</li>");
        sb.append("<li><code>SimpleDataSource</code>：阻塞队列 + Semaphore 的连接池实现。</li>");
        sb.append("<li><code>SimpleJavaLexer</code>：零依赖 Java 词法分析器，支撑依赖分析与方法调用链。</li>");
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
        sb.append("<p>基于 JDBC 元数据 API 实现的轻量级数据库客户端，支持多数据源、MySQL 元数据浏览、表结构查看、数据预览、SQL 查询与 ER 视图拖拽建模。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li>多数据源管理：支持数据源增删改查、连接测试、驱动 Jar 上传与管理。</li>");
        sb.append("<li>树形导航：连接 → 数据库 → 表 → 列/视图/索引，支持动态加载。</li>");
        sb.append("<li>表属性页面：显示表基本信息（引擎、字符集）和列详细信息（类型、可空、自增等）。</li>");
        sb.append("<li>数据视图：支持分页查询表数据。</li>");
        sb.append("<li>SQL 查询：内置 SQL 编辑器，支持语法提示、格式化、示例 SQL、结果表格展示。</li>");
        sb.append("<li>ER 视图：支持拖拽表到画布生成视图，Ctrl 多选创建关联，字段转换节点，自动生成 SQL。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>DatabaseMetadataService</code>：封装 JDBC DatabaseMetaData，提供 <code>getTables()</code>、<code>getColumns()</code> 等方法。</li>");
        sb.append("<li><code>DataModel</code>：统一的行列数据模型，支持转换为 JSON、HTML 表格、CSV 等格式。</li>");
        sb.append("<li><code>DynamicDataSourceManager</code>：动态加载驱动、创建连接池、支持热刷新。</li>");
        sb.append("<li><code>DynamicSqlExecutor</code>：动态 SQL 解析与分页查询，自动适配 MySQL/Oracle 方言。</li>");
        sb.append("<li>前端采用原生 JavaScript 动态构建树与画布，无第三方库。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildCodeStatsDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"codestats-detail\">");
        sb.append("<h2>📊 代码分析模块</h2>");
        sb.append("<p>功能完备的代码统计与分析工具，支持多线程分析、多种语言识别、注释排除、复杂度估算、类依赖图、方法级调用链，并可生成 JSON/CSV 报告。</p>");
        sb.append("<h3>⚡ 核心功能</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>文件收集</strong>：递归扫描目录，支持扩展名过滤、排除路径、.gitignore 规则、额外路径追加。</li>");
        sb.append("<li><strong>并行分析</strong>：可配置线程数，每个文件独立分析行数、代码行、注释行、空行、复杂度（关键字计数）。</li>");
        sb.append("<li><strong>语言识别</strong>：内置 30+ 种语言（Java、Python、JS、Go 等），通过扩展名或 shebang 行识别，支持自定义注释规则。</li>");
        sb.append("<li><strong>依赖分析</strong>：基于手写 Java 词法分析器提取类依赖，构建依赖图，检测循环依赖，支持出向/入向依赖查询。</li>");
        sb.append("<li><strong>方法级调用链</strong>：手写方法抽取器，零第三方依赖，支持调用树构建、PlantUML 时序图导出、入口方法识别。</li>");
        sb.append("<li><strong>结果输出</strong>：支持 JSON / CSV / Console 格式，并可选择将收集的文件内容合并为一个文件（带路径注释）。</li>");
        sb.append("<li><strong>GUI 模式</strong>：提供 Swing 图形界面，可实时预览分析进度与结果表格。</li>");
        sb.append("<li><strong>3D 可视化</strong>：依赖图 3D 力导向图、3D 柱状图、调用链拓扑图。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔍 使用方式</h3>");
        sb.append("<p><strong>Web 模式</strong>：点击顶部“代码分析”标签，填写根目录、扩展名等参数，点击“开始分析”，结果以表格形式展示，支持导出 CSV 和下载合并文件。</p>");
        sb.append("<p><strong>命令行模式</strong>：执行 <code>java -jar codestats.jar -d ./src -e java -o json</code> 等参数。</p>");
        sb.append("<p><strong>GUI 模式</strong>：运行 <code>com.omni.framework.codestats.gui.CodeStatsGUI</code> 主类。</p>");
        sb.append("<p><strong>子功能</strong>：代码分析页签内还集成“网页爬虫”与“题库分析”两个子模式。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildFilesDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"files-detail\">");
        sb.append("<h2>📁 目录管理模块</h2>");
        sb.append("<p>提供基于 HTTP 的文件浏览、读取、上传、下载、编辑功能，支持路径安全校验，可作为轻量级文件管理器。</p>");
        sb.append("<h3>📌 功能规划</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>文件列表</strong>：GET <code>/demo/files/api/list?path=/xxx</code> 返回目录下的文件和子目录 JSON。</li>");
        sb.append("<li><strong>文件读取</strong>：GET <code>/demo/files/api/read?path=/xxx/file.txt</code> 返回文件内容。</li>");
        sb.append("<li><strong>文件上传</strong>：POST <code>/demo/files/api/upload</code> 支持 base64 内容上传文件到指定路径。</li>");
        sb.append("<li><strong>编辑保存</strong>：POST <code>/demo/files/api/save</code> 保存编辑后的文本文件。</li>");
        sb.append("<li><strong>新建/重命名/删除</strong>：支持目录与文件的创建、重命名、删除。</li>");
        sb.append("<li><strong>下载</strong>：支持单文件下载与目录 ZIP 打包下载。</li>");
        sb.append("<li><strong>双根目录</strong>：支持本地目录与服务器目录（默认 D:/temp）切换。</li>");
        sb.append("<li><strong>安全防护</strong>：路径遍历攻击防范（禁止包含 ..），仅允许操作配置的根目录。</li>");
        sb.append("</ul>");
        sb.append("<h3>💡 技术实现</h3>");
        sb.append("<p>后端使用 <code>java.nio.file.Files</code> API，通过 <code>FileUtils</code> 工具类完成安全路径解析、目录遍历、ZIP 打包。前端基于 Fetch API 实现文件浏览器与在线编辑器。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildAgentDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"agent-detail\">");
        sb.append("<h2>🤖 智能 Agent</h2>");
        sb.append("<p>AI 驱动的开发助手，集成多种大语言模型后端，支持自然语言代码生成、解释、项目知识库问答等能力。</p>");
        sb.append("<h3>✨ 核心功能</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>多后端支持</strong>：本地 Ollama、DeepSeek 官网 API、自定义 OpenAI 兼容 API、内置项目助手（知识库 + n-gram 降级）。</li>");
        sb.append("<li><strong>对话管理</strong>：支持会话 ID、上下文消息列表、温度调节、模型切换。</li>");
        sb.append("<li><strong>Ollama 部署</strong>：提供模型状态检查、模型拉取、模型运行测试的 Web 界面。</li>");
        sb.append("<li><strong>项目助手</strong>：内置 JSON 知识库，支持关键词匹配、本地缓存、CharNgramLM 降级生成，可回答 1000+ 常规框架问题。</li>");
        sb.append("<li><strong>流式输出预留</strong>：架构支持 WebSocket 实时通信，可扩展流式响应。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术架构</h3>");
        sb.append("<ul>");
        sb.append("<li>后端：Java HttpClient 调用 Ollama / DeepSeek / 自定义 API，统一封装 <code>AIChatService</code>。</li>");
        sb.append("<li>项目助手：<code>ProjectAssistantService</code> 加载知识库、匹配答案、n-gram 降级生成。</li>");
        sb.append("<li>前端：聊天 UI 支持 Markdown 渲染、代码高亮、加载动画、消息历史。</li>");
        sb.append("<li>接口：<code>/agent/api/chat</code> (HTTP)、<code>/agent/api/ollama/*</code> (部署管理)。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildOpenSourceDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"opensource-detail\">");
        sb.append("<h2>🧰 开源工具</h2>");
        sb.append("<p>DeepSeek 官网智能对话代理，通过后端安全转发 API 请求，支持连续上下文、多轮问答。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li>会话管理：基于 sessionId 保持上下文，支持多轮连续对话。</li>");
        sb.append("<li>后端代理：隐藏 API Key，统一超时控制与错误处理。</li>");
        sb.append("<li>前端聊天 UI：消息气泡、加载状态、状态提示、自动滚动。</li>");
        sb.append("<li>接口：<code>/deepseek/api/chat</code>、<code>/deepseek/api/close</code>。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 使用方式</h3>");
        sb.append("<p>在顶部导航点击“开源工具”标签，输入问题即可与 DeepSeek 进行连续对话。后端需配置 DeepSeek API Key。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildApiExplorerDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"apiexplorer-detail\">");
        sb.append("<h2>🔌 API 调试模块</h2>");
        sb.append("<p>Swagger 风格接口文档自动生成与在线调试工具，支持参数填充、请求发送、响应展示、自定义 HTTP 请求。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>接口文档</strong>：扫描所有 Controller，按模块分组展示 API 列表，显示方法、路径、描述、参数、请求体、响应格式。</li>");
        sb.append("<li><strong>在线调试</strong>：自动生成参数输入框，支持 Query/Path 参数、JSON Body、一键填充示例值、JSON 格式化。</li>");
        sb.append("<li><strong>请求发送</strong>：内置 fetch 请求发送，显示状态码、响应头、格式化响应体。</li>");
        sb.append("<li><strong>自定义请求</strong>：独立标签页，支持任意 URL、Method、Headers、Body（JSON/Form/Text），类似 Postman 的轻量替代。</li>");
        sb.append("<li><strong>搜索过滤</strong>：左侧树支持按路径/摘要搜索过滤。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>SwagHandler</code>：反射扫描 Controller，提取 @RequestMapping、@ApiOperation、参数注解，构建 ApiDefinition。</li>");
        sb.append("<li><code>SwagService</code>：业务层封装，支持模块排序、权限过滤扩展。</li>");
        sb.append("<li>前端：原生 JS 构建树形导航、参数表格、响应展示，自定义请求支持 JSON/Form/Text 三种 Body 格式。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildThree3DDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"three3d-detail\">");
        sb.append("<h2>🌐 3D 依赖图与可视化</h2>");
        sb.append("<p>基于 Three.js 与 ECharts GL 的 3D 可视化模块，提供项目模块拓扑、代码依赖图、调用链、实时监控等多种 3D 场景。</p>");
        sb.append("<h3>✨ 子功能</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>项目模块</strong>：3D 球体节点展示项目模块，中心发光球 + 环绕模块 + 连接线，点击查看详情。</li>");
        sb.append("<li><strong>3D 柱状图</strong>：各语言代码行数 3D 柱状图，支持自动旋转与悬停提示。</li>");
        sb.append("<li><strong>调用链拓扑</strong>：系统全链路 / 项目调用链 3D 拓扑图，按 Controller/Service/Mapper 分层布局，箭头表示调用方向。</li>");
        sb.append("<li><strong>动态调用链</strong>：流光粒子沿调用路径运动，模拟请求处理过程。</li>");
        sb.append("<li><strong>实时监控</strong>：线程状态柱状图、堆内存环形柱、连接池水位、请求统计卡片、线程粒子流。</li>");
        sb.append("<li><strong>JVM 监控</strong>：GC 耗时/堆内存/类加载/区域使用率折线图 + 3D 堆内存柱状图。</li>");
        sb.append("<li><strong>系统监控</strong>：CPU、内存、磁盘、GPU、进程、线程等系统组件卡片与详情浮层。</li>");
        sb.append("<li><strong>方法级调用链</strong>：3D 调用树 + PlantUML 时序图 + 邻居方法快速跳转。</li>");
        sb.append("<li><strong>配置化场景</strong>：通过 JSON 配置驱动 SceneEngine 构建 3D 场景，支持 primitive/model/group/sprite、布局、交互、动画。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li>Three.js + OrbitControls + CSS2DRenderer 实现 3D 场景与标签。</li>");
        sb.append("<li>ECharts + ECharts GL 实现 3D 柱状图与 2D 折线图。</li>");
        sb.append("<li><code>SceneEngine</code>：配置驱动的场景引擎，包含 ResourceManager、ObjectFactory、InteractionManager、AnimationManager、Layouts。</li>");
        sb.append("<li>数据来源：<code>/3d/api/*</code> 系列接口，复用代码分析、JVM 监控、系统监控数据。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildScriptEngineDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"scriptengine-detail\">");
        sb.append("<h2>⚙️ 脚本引擎</h2>");
        sb.append("<p>多语言脚本在线编辑与执行模块，支持 Python、Java、C、C++、REST 五种执行器，提供源码编译、参数传递、结果捕获。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>多语言支持</strong>：Python、Java、C、C++、REST，通过 <code>ScriptExecutorRegistry</code> 注册与调度。</li>");
        sb.append("<li><strong>执行模式</strong>：源码（需编译）、二进制/Class、JAR、目录四种模式。</li>");
        sb.append("<li><strong>参数传递</strong>：支持 JSON 参数、paramTemplate 参数映射、脚本参数注入。</li>");
        sb.append("<li><strong>在线编辑</strong>：左侧参数配置 + 源码编辑器双标签页，支持示例插入、清空、字符统计、光标位置。</li>");
        sb.append("<li><strong>执行结果</strong>：右侧结果面板实时展示 stdout/stderr，支持复制。</li>");
        sb.append("<li><strong>文件上传</strong>：支持上传脚本文件并自动执行。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>BaseScriptExecutor</code>：模板方法封装预处理、源码解析、执行准备、进程运行、结果后处理。</li>");
        sb.append("<li><code>JavaExecutor</code>：使用 JavaCompiler API 编译源码，URLClassLoader 加载类，反射调用方法。</li>");
        sb.append("<li><code>PythonExecutor</code>：写入临时 .py 文件，调用 python 命令，参数以 JSON 传递。</li>");
        sb.append("<li><code>CExecutor / CppExecutor</code>：调用 gcc/g++ 编译，生成可执行文件后运行。</li>");
        sb.append("<li>前端：iframe 双页签架构，postMessage 与父页面通信，统一执行入口。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildAutoTestDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"autotest-detail\">");
        sb.append("<h2>⚡ 自动化测试模块</h2>");
        sb.append("<p>轻量级 Web 自动化测试平台，包含项目管理、用例管理、执行中心、报告中心四大子模块。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>项目管理</strong>：项目增删改查、项目编码、描述、状态管理。</li>");
        sb.append("<li><strong>用例管理</strong>：用例名称、编号、模块路径、优先级、环境、浏览器、基础 URL、超时、重试、标签管理。</li>");
        sb.append("<li><strong>测试步骤</strong>：支持 goto/click/input/wait 等动作，定位器类型与值、等待策略、失败处理。</li>");
        sb.append("<li><strong>断言管理</strong>：支持文本断言、URL 断言，断言顺序、预期值、比较类型、失败处理。</li>");
        sb.append("<li><strong>执行中心</strong>：选择用例执行，显示执行状态、通过率、执行日志。</li>");
        sb.append("<li><strong>报告中心</strong>：执行记录列表，显示执行编号、开始时间、总步骤、通过/失败数、通过率、状态、耗时。</li>");
        sb.append("<li><strong>扩展能力</strong>：测试数据、变量、前后置钩子、执行快照等 Mapper 已预留。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>Pipeline</code>：执行流水线，加载步骤与断言，逐个执行并收集结果。</li>");
        sb.append("<li><code>StepExecutor</code>：抽象步骤执行器，支持前置处理、动作执行、断言执行、失败处理。</li>");
        sb.append("<li><code>ActionHandlerRegistry</code>：注册 goto/click/input/wait 动作处理器。</li>");
        sb.append("<li><code>AssertionHandlerRegistry</code>：注册 text/url 断言处理器。</li>");
        sb.append("<li><code>VariableResolver</code>：支持 <code>{{var}}</code> 变量替换。</li>");
        sb.append("<li>前端：iframe 四页签架构，项目管理 → 用例管理 → 执行中心 → 报告中心联动。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildBuilderDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"builder-detail\">");
        sb.append("<h2>🧱 页面搭建模块</h2>");
        sb.append("<p>可视化拖拽页面搭建器，支持基础元素与组件元素、属性编辑、HTML/JSON 导入导出、实时预览。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>元素库</strong>：基础元素（文本、标题、按钮、图片、输入框、链接、容器）+ 组件元素（卡片、提示条、表单、表格、导航栏）。</li>");
        sb.append("<li><strong>拖拽搭建</strong>：左侧元素拖入画布，支持绝对定位、网格吸附、拖拽移动、选中高亮。</li>");
        sb.append("<li><strong>属性编辑</strong>：右侧属性面板实时编辑元素 props，支持数字/文本/颜色/下拉等类型。</li>");
        sb.append("<li><strong>导出功能</strong>：导出干净 HTML、导出 JSON 配置、导入 JSON 恢复画布、新窗口预览。</li>");
        sb.append("<li><strong>快捷键</strong>：Delete 删除选中元素、Ctrl+D 复制元素。</li>");
        sb.append("<li><strong>扩展性</strong>：基于 <code>BaseElement</code> 抽象基类与 <code>ElementRegistry</code> 注册中心，新增元素只需继承并注册。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>CanvasManager</code>：画布管理器，负责元素实例管理、选中、拖拽、属性更新、通知 UI。</li>");
        sb.append("<li><code>BaseElement</code>：元素抽象基类，定义 meta 元数据、createDOM、render、updateProps、toJSON。</li>");
        sb.append("<li><code>ElementPanel</code>：左侧抽屉元素库，按分类展示，支持 HTML5 dragstart。</li>");
        sb.append("<li><code>PropertyPanel</code>：右侧属性面板，根据选中元素动态生成编辑表单。</li>");
        sb.append("<li><code>Toolbar</code> + <code>Exporter</code>：顶部工具栏，支持清空、预览、导出 HTML/JSON、导入 JSON。</li>");
        sb.append("<li>纯 ES Module 前端，无框架依赖，代码结构清晰。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildSystemMonitorDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"systemmonitor-detail\">");
        sb.append("<h2>🖥️ 系统监控模块</h2>");
        sb.append("<p>基于 JVM ManagementFactory 与系统命令的系统实时监控模块，采用组件化架构，支持分类过滤、卡片概览、详情浮层、自动刷新。</p>");
        sb.append("<h3>✨ 监控组件</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>CPU</strong>：核心数、架构、系统负载、CPU 使用率、进程 CPU 占用。</li>");
        sb.append("<li><strong>物理内存</strong>：总量、已用、空闲、使用率。</li>");
        sb.append("<li><strong>磁盘</strong>：各分区容量、已用、剩余、使用率。</li>");
        sb.append("<li><strong>文件系统</strong>：文件系统类型、挂载点、总空间、可用空间、工作目录。</li>");
        sb.append("<li><strong>GPU</strong>：显卡型号、显存、驱动版本（Windows wmic / Linux lspci）。</li>");
        sb.append("<li><strong>声卡</strong>：音频设备列表（Windows wmic / Linux /proc/asound）。</li>");
        sb.append("<li><strong>进程</strong>：总进程数、当前 PID、CPU 占用 Top 进程列表。</li>");
        sb.append("<li><strong>线程</strong>：线程总数、峰值、守护线程数、状态分布、死锁检测。</li>");
        sb.append("<li><strong>Java 运行时</strong>：JVM 版本/厂商/启动参数/运行时长、堆/非堆内存、内存池、GC 统计、类加载、JIT 编译。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>SystemComponent</code> 接口 + <code>AbstractSystemComponent</code> 抽象类，统一组件契约。</li>");
        sb.append("<li><code>SystemMonitorEngine</code>：组件注册中心，支持动态注册/注销、缓存采集结果。</li>");
        sb.append("<li><code>MonitorItemInfo</code>：统一信息模型，区分 coreInfo / runtimeInfo / status。</li>");
        sb.append("<li>前端：分类侧边栏 + 卡片网格 + 详情浮层，支持 1s/2s/5s/10s 刷新间隔、暂停/继续。</li>");
        sb.append("<li>接口：<code>/systemmonitor/api/components</code>、<code>/all</code>、<code>/item</code>、<code>/category</code>、<code>/summary</code>。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildQuestionBankDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"questionbank-detail\">");
        sb.append("<h2>📚 题库分析模块</h2>");
        sb.append("<p>面向题库 Excel/TXT 文件的合并、搜索、预览、导出与 OCR 识别工具，支持屏幕选区识别并自动匹配题目。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>文件扫描合并</strong>：按根目录、关键词、文件名前缀扫描 .xls/.xlsx/.txt，合并为统一 CSV 行格式。</li>");
        sb.append("<li><strong>自动合并保障</strong>：未检测到合并文件时自动触发合并。</li>");
        sb.append("<li><strong>关键词搜索</strong>：合并后按关键词模糊搜索，返回第一条匹配行。</li>");
        sb.append("<li><strong>数据预览</strong>：分页展示合并数据，支持关键词过滤。</li>");
        sb.append("<li><strong>Excel 导出</strong>：将预览数据导出为 .xlsx 文件，支持关键词过滤。</li>");
        sb.append("<li><strong>OCR 识别</strong>：屏幕选区截图 → Tesseract OCR 识别 → 自动填充搜索关键词。</li>");
        sb.append("<li><strong>截图保存</strong>：识别后支持保存截图为 PNG。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>QuestionBankService</code>：配置加载、文件扫描、Excel/TXT 合并、CSV 解析、预览缓存、Excel 导出。</li>");
        sb.append("<li><code>ExcelUtil</code>：基于 Apache POI 解析 Excel 为 CSV 行。</li>");
        sb.append("<li><code>OcrController</code>：Tesseract OCR 识别上传图片。</li>");
        sb.append("<li>前端：html2canvas 截取页面区域 + 表单上传 OCR + 搜索结果展示。</li>");
        sb.append("<li>接口：<code>/questionbank/api/config</code>、<code>/scan</code>、<code>/merge</code>、<code>/search</code>、<code>/preview</code>、<code>/exportExcel</code>、<code>/ocr</code>。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }

    private String buildSpiderDetail() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"spider-detail\">");
        sb.append("<h2>🕷️ 网页爬虫模块</h2>");
        sb.append("<p>面向 CSDN 博客的批量文章下载工具，支持并发控制、请求间隔、失败链接记录、合并打包、作者信息采集。</p>");
        sb.append("<h3>✨ 功能特性</h3>");
        sb.append("<ul>");
        sb.append("<li><strong>任务启动</strong>：输入 CSDN 用户名，配置并发数、请求间隔、最大文章数、最大时间。</li>");
        sb.append("<li><strong>实时状态</strong>：当前步骤、当前 URL、成功/失败数量、进度条。</li>");
        sb.append("<li><strong>文章列表</strong>：成功文章表格（标题、阅读数、下载按钮），失败文章表格（URL、原因）。</li>");
        sb.append("<li><strong>合并下载</strong>：所有文章合并为一个 TXT，失败链接自动追加到合并文件末尾。</li>");
        sb.append("<li><strong>单篇下载</strong>：支持单篇文章内容下载。</li>");
        sb.append("<li><strong>作者信息</strong>：采集昵称、简介、访问量、原创数、粉丝数、关注数、点赞数、评论数、收藏数、排名、专栏列表。</li>");
        sb.append("<li><strong>Playwright 支持</strong>：可选启用 Playwright 无头浏览器对抗反爬。</li>");
        sb.append("</ul>");
        sb.append("<h3>🔧 技术实现</h3>");
        sb.append("<ul>");
        sb.append("<li><code>SpiderTaskService</code>：任务管理、异步执行、状态上下文 <code>TaskContext</code>、作者信息采集。</li>");
        sb.append("<li><code>Crawler</code>：核心爬虫引擎，文章列表抓取、分页处理、线程池并发、合并文件生成。</li>");
        sb.append("<li><code>PageParser</code>：Jsoup 解析文章列表、分页链接、文章详情。</li>");
        sb.append("<li><code>ArticleProcessor</code>：单篇文章下载、解析、存储。</li>");
        sb.append("<li><code>FileStorage</code>：文章按标题保存为 TXT 文件。</li>");
        sb.append("<li><code>HttpUtil</code>：Playwright 优先，普通 HTTP 降级。</li>");
        sb.append("<li>接口：<code>/spider/api/start</code>、<code>/status</code>、<code>/articles</code>、<code>/download</code>、<code>/article</code>。</li>");
        sb.append("</ul>");
        sb.append("</div>");
        return sb.toString();
    }
}