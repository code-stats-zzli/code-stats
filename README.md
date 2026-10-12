<!-- ========== AI 生成内容标识（依据《人工智能生成合成内容标识办法》） ========== -->

# CodeStats – 综合性开发工具平台

>  **AI 辅助声明**  
> 本项目**大量使用 AI 辅助编程**（deepseek）。
> - 约 **100% 的代码**由 AI 生成初稿，经人工审查、调试、合并后形成最终版本。
> - AI 生成的代码主要集中在：**控制器、服务层、MyBatis 代理、前端 Vue 组件、配置文件、重复性 CRUD 逻辑**。
> - 人工贡献部分：**架构设计、核心 IoC/Tomcat 容器、依赖分析引擎、安全校验、项目助手知识库**。

> **透明性承诺**
> - 本仓库不包含任何训练数据或模型，仅发布最终可执行代码。

> **法律责任声明**  
> 根据《人工智能生成合成内容标识办法》，使用者应知晓本项目中部分内容可能由 AI 生成，不构成对原著作权或专利权的放弃。原作者保留对人工撰写部分的完整权利。


CodeStats 项目介绍与推荐
一份融合了项目介绍、架构解析、模块详解与推荐指南的完整文档
基于完整项目源码整理，适合作为 README 或项目宣传材料

一、项目简介
CodeStats 是一个完全自研的轻量级 Java Web 平台，底层基于自研的 Mini Tomcat、Mini Spring、Mini MVC、Mini MyBatis，从零构建企业级框架的核心技术。项目不依赖任何第三方 Web 框架（Tomcat / Spring / MyBatis），所有核心组件均为自研实现。

平台集成了数据库管理、代码分析、目录管理、AI Agent、自动化测试、API 调试、3D 可视化、脚本引擎、系统监控、题库分析、网页爬虫、截图助手等十余个功能模块，既可作为开发工具集使用，也可作为学习 Java Web 底层原理的实战项目。

二、为什么推荐 CodeStats？
2.1 推荐人群
强烈推荐以下人群阅读、学习或使用本项目：

Java 初中级开发者：想深入理解 Spring、Tomcat、MyBatis 底层原理，但阅读源码时感到吃力。CodeStats 用简洁清晰的代码实现了这些框架的核心机制，是绝佳的“源码入门替代品”。

计算机专业学生 / 应届生：准备校招面试，需要项目经验证明技术深度。项目涵盖 IoC、AOP、MVC、连接池、线程池、设计模式等高频考点，且每个模块都可以独立讲透。

全栈开发学习者：项目前后端均由自研框架实现（SimpleVue 模拟 Vue 的响应式、模板编译、组件化），可一站式学习前后端框架的核心设计思想。

对 AI Agent 集成感兴趣的开发者：项目集成 Ollama 本地模型、DeepSeek API、自定义 OpenAI 兼容 API 以及项目助手（知识库 + n-gram 降级），提供完整的 Web 聊天界面。

需要快速搭建工具平台的开发者：CodeStats 本身就是一个可运行的开发工具平台，包含数据库浏览器、代码分析、文件管理、AI 聊天、自动化测试等功能，可直接作为基础平台二次开发。

想了解 JVM 监控与 3D 可视化的开发者：项目内置 JVM 监控（GC、线程、堆、类加载）、系统监控（CPU、内存、磁盘、进程）、3D 调用链拓扑、实时粒子流等，技术覆盖面广。

2.2 推荐理由
完全自研，无框架依赖：Web 服务器、IoC 容器、MVC、ORM、连接池、日志、缓存、表达式引擎、脚本执行引擎全部自研，代码透明可控。

可读性与完整性平衡：很多教学项目要么过于简单（一个 IoC 容器就结束），要么过于庞大（无法在几天内读完）。CodeStats 恰好覆盖了企业级 Java 平台的核心组件，每个模块代码量适中，配合清晰注释与合理分包，适合精读。

功能丰富，开箱即用：内置十余个功能模块，每个模块都有完整的 Web 界面和 REST API，可直接运行体验。

注解驱动，约定优于配置：支持 @Controller、@Service、@Repository、@Autowired、@RequestMapping、@Select 等注解，包扫描自动装配。

Pipeline 责任链，易扩展：请求处理、代码分析、依赖分析均采用 Pipeline-Value 模式，新增功能只需增加 Stage 或 Valve。

AI 辅助透明：明确标注 AI 生成与人工贡献范围，人工贡献集中在架构设计与核心容器实现，学习者可清楚知道哪些是需要深度思考的部分。

实战学习价值高：从零实现 Web 容器、IoC、ORM、连接池、日志、缓存、表达式引擎、脚本引擎，是理解 Java 企业级开发底层原理的绝佳路径。

三、核心特性
完全自研框架：嵌入式 Tomcat、IoC 容器、MVC、ORM、连接池、日志、缓存、表达式引擎、脚本执行引擎等全部自研。

零第三方框架依赖：核心 Web 容器、IoC、MVC、ORM 均不依赖 Spring / Tomcat / MyBatis。

模块化设计：功能模块可插拔，数据库、AI、爬虫等模块均为可选配置。

注解驱动：支持 @Controller、@Service、@Repository、@Autowired、@RequestMapping、@Select 等注解。

Pipeline 责任链：请求处理、代码分析、依赖分析均采用 Pipeline 模式，易于扩展。

前后端一体：内置自研前端框架 SimpleVue（响应式、模板编译、组件化），并提供多套功能页面。

丰富的功能模块：数据库浏览器、代码分析、目录管理、AI Agent、自动化测试、API 调试、3D 可视化、脚本引擎、系统监控、题库分析、网页爬虫、截图助手等。

AI 辅助声明：项目大量使用 AI 辅助编程，人工贡献集中在架构设计与核心容器实现。

四、技术架构
4.1 分层架构
text
┌─────────────────────────────────────────────────────────────┐
│                        前端层（SimpleVue）                    │
│  响应式数据绑定 / 模板编译 / 组件化 / 路由 / 事件系统            │
├─────────────────────────────────────────────────────────────┤
│                      业务功能层（Modules）                    │
│  数据库 | 代码分析 | 目录管理 | AI Agent | 自动化测试 | API 调试 │
│  3D 可视化 | 脚本引擎 | 系统监控 | 题库分析 | 网页爬虫 | 截图助手 │
├─────────────────────────────────────────────────────────────┤
│                    自研框架层（Framework）                    │
│  Mini Tomcat | Mini Spring | Mini MVC | Mini MyBatis          │
│  连接池 | 日志 | 缓存 | 表达式引擎 | 脚本执行引擎 | Swagger 风格 │
├─────────────────────────────────────────────────────────────┤
│                    基础设施层（Infrastructure）               │
│  JDK 8+ | Maven | MySQL(可选) | Ollama(可选) | DeepSeek API   │
└─────────────────────────────────────────────────────────────┘
4.2 核心设计原则
零第三方框架依赖：Web、IoC、MVC、ORM、连接池、日志、缓存、前端全部自研。

模块可插拔：数据库、AI、爬虫等模块均为可选，不配置不影响主流程启动。

约定优于配置：注解驱动，包扫描自动装配。

Pipeline 责任链：请求处理、代码分析、依赖分析均采用 Pipeline-Value 模式。

AI 辅助透明：明确标注 AI 生成与人工贡献范围。

五、模块完整介绍
5.1 自研框架层
模块	功能	核心组件	技术点
Mini Tomcat	自研 Web 服务器、Servlet 容器	Connector、Pipeline、Servlet、WebappClassLoader	Socket、线程池、责任链、类加载、HTTP 解析
Mini Spring	IoC 容器、依赖注入、单例管理	SimpleApplicationContext、@Autowired、BeanFactory	反射、注解、递归注入、单例模式
Mini MVC	路由映射、参数绑定、JSON 返回	DispatcherServlet、HandlerMapping、ParamBinder	反射调用、参数解析、JSON 序列化
Mini MyBatis	注解 SQL、动态代理、结果映射	MapperProxy、JdbcTemplate、@Select 等	动态代理、JDBC、反射
连接池	连接复用、超时管理、有效性检测	阻塞队列、Semaphore、清理线程	并发、信号量、阻塞队列
日志框架	分级日志、多 Appender、XML 配置	Logger、Appender、Layout、Level	单例、工厂、策略、XML 解析
缓存框架	本地缓存、TTL、定期清理	ConcurrentHashMap、过期线程	并发容器、定时任务
表达式引擎	支持变量、函数、属性访问、数组、三元运算	Lexer、Parser、Evaluator、VariableResolver	递归下降解析、AST、反射
脚本执行引擎	支持 Python、Java、C、C++ 脚本执行	ScriptEngine、ScriptExecutor、PythonExecutor 等	进程调用、动态编译、类加载
Swagger 风格 API	扫描 Controller 生成 API 元数据	SwagHandler、ApiDefinition、@ApiModule	注解扫描、反射、JSON
代码分析引擎	统计行数、注释、语言分布、复杂度	CollectStage→ParallelAnalyzeStage→AggregateStage→OutputStage	Pipeline、线程池、GitIgnore、正则
依赖分析引擎	类依赖图、循环依赖检测、方法调用链	JavaDependencyExtractor、MethodExtractor、CallTreeBuilder	词法分析、图算法、PlantUML 导出
JVM 监控	GC、线程、堆、类加载、JVM 参数	JvmMonitor、CommandExecutor、各 Parser	JMX、外部命令调用、解析
5.2 业务功能层
模块	功能	核心组件
项目介绍	平台导航、架构说明、模块详情	IndexController、IntroProjectController
数据库浏览器	多数据源管理、元数据浏览、表数据分页、SQL 查询	DatabaseClientController、DatabaseMetadataService、TableDataService
代码分析	多线程行数统计、语言识别、依赖分析、方法调用链	CodeAnalysisController、MethodAnalysisController
目录管理	文件浏览、编辑、上传下载、ZIP 打包	FilesController、DirectoryService
AI Agent	多轮对话、Ollama/DeepSeek/自定义 API/项目助手	AgentController、AIChatController、AIChatService、ProjectAssistantService
自动化测试	项目管理、用例管理、执行引擎、报告中心	ProjectController、TestCaseController、TestExecutionController、Pipeline
API 调试	Swagger 风格接口文档、在线调试、自定义请求	ApiExplorerController、SwagService
3D 可视化	项目模块图、柱状图、调用链拓扑、动态调用链、实时监控、JVM 监控、方法级调用链、系统监控	ThreeDController、ThreeDGraphService、JstatsController、SystemMonitorController
脚本引擎	在线编辑、多语言执行、参数配置、结果展示	ScriptEngineController、ScriptExecutionService
系统监控	CPU、内存、磁盘、GPU、进程、线程、文件系统、Java 运行时	SystemMonitorController、SystemMonitorEngine、各 Component
题库分析	Excel 合并、数据预览、搜索、导出、OCR 识别	QuestionBankController、OcrController、QuestionBankService
网页爬虫	CSDN 博客爬取、合并、失败链接追加	SpiderController、SpiderTaskService
截图助手	全屏/区域截图、OCR 识别、搜索题库	ScreenshotAssistantBootstrap、ScreenshotService、OcrService
数据源管理	多数据源配置、驱动管理	DataSourceController、DriverController
5.3 前端层
模块	功能
SimpleVue	自研前端框架，支持响应式数据、模板编译、组件化、样式隔离
demo 平台	完整的 Web 界面，包含所有功能模块的 HTML/JS/CSS
face 重构版	基于 SimpleVue 重构的前端界面，展示组件化开发
vue 核心库	SimpleVue 的核心实现：Observer、Compiler、Watcher、Dep、StyleManager
六、项目结构
text
CodeStats/
├── src/main/java/com/omni/
│   ├── Bootstrap.java                 # 启动入口
│   ├── business/assistance/           # 业务模块
│   │   ├── agent/                     # AI Agent
│   │   ├── api/                       # API 调试
│   │   ├── autotest/                  # 自动化测试
│   │   ├── codestat/                  # 代码分析
│   │   ├── directory/                 # 目录管理
│   │   ├── introduction/              # 项目介绍
│   │   ├── jstats/                    # JVM 监控
│   │   ├── questionbank/              # 题库分析
│   │   ├── screenshot/                # 截图助手
│   │   ├── scriptengine/              # 脚本引擎
│   │   ├── spider/                    # 网页爬虫
│   │   ├── systemmonitor/             # 系统监控
│   │   ├── table/mysql/               # 数据库管理
│   │   └── three3d/                   # 3D 可视化
│   ├── framework/                     # 自研框架
│   │   ├── tomcat/                    # Mini Tomcat
│   │   ├── spring/                    # Mini Spring
│   │   ├── mybatis/                   # Mini MyBatis
│   │   ├── jdbc/                      # JDBC 工具
│   │   ├── pool/                      # 连接池
│   │   ├── log/                       # 日志框架
│   │   ├── cache/                     # 缓存框架
│   │   ├── expression/                # 表达式引擎
│   │   ├── scriptexec/                # 脚本执行引擎
│   │   ├── swag/                      # Swagger 风格 API
│   │   ├── spider/                    # 爬虫核心
│   │   ├── jstats/                    # JVM 监控核心
│   │   ├── v4j/                       # SimpleVue 后端支持
│   │   └── web/                       # Web 跟踪上下文
│   └── common/                        # 公共工具
│       ├── cache/                     # 缓存接口与实现
│       ├── datasource/                # 动态数据源
│       ├── excel/                     # Excel 工具
│       └── util/                      # 通用工具
├── src/main/resources/
│   ├── application.properties         # 应用配置
│   ├── config.yaml                    # 模块配置
│   ├── log-config.xml                 # 日志配置
│   └── project_knowledge.json         # 项目助手知识库
├── webapps/
│   ├── demo/                          # 完整 Web 界面
│   │   ├── index.html                 # 主入口
│   │   ├── agent/                     # AI Agent 页面
│   │   ├── apiexplorer/               # API 调试页面
│   │   ├── autotest/                  # 自动化测试页面
│   │   ├── builder/                   # 可视化页面搭建器
│   │   ├── code/                      # 代码分析页面
│   │   ├── db/                        # 数据库管理页面
│   │   ├── docs/                      # 项目介绍页面
│   │   ├── files/                     # 目录管理页面
│   │   ├── opensource/                # 开源工具页面
│   │   ├── questionbank/              # 题库分析页面
│   │   ├── scriptengine/              # 脚本引擎页面
│   │   ├── spider/                    # 爬虫页面
│   │   └── three/                     # 3D 可视化页面
│   ├── face/                          # SimpleVue 重构版前端
│   └── vue/                           # SimpleVue 核心库
└── pom.xml
七、快速开始
7.1 环境要求
JDK 8+（推荐 JDK 11+）

Maven 3.6+

MySQL（可选，用于数据库模块）

Ollama（可选，用于本地 AI 模型）

7.2 构建与运行
bash
# 克隆仓库
git clone https://gitee.com/zhouzuoli/code-stats.git
cd code-stats

# 编译打包
mvn clean package

# 启动主程序（内置 Tomcat 服务器）
java -cp target/CodeStats-1.0.jar com.omni.run.Bootstrap
启动后访问：http://localhost:28080/omni/demo/index.html
（端口和 context path 可在 application.properties 中修改）

7.3 配置说明
在 src/main/resources/application.properties 中配置：

properties
# 服务器端口
server.port=28080

# Context Path
context.path=/omni

# 数据库配置（可选）
db.url=jdbc:mysql://localhost:3306/yourdb?useSSL=false
db.username=root
db.password=123456

# AI 配置（可选）
ollama.api.url=http://localhost:11434/api/chat
deepseek.api.key=sk-xxxxxx
八、技术亮点
完全自研的 Web 容器：Connector + Pipeline + Servlet 容器 + WebappClassLoader，实现类隔离与热部署。

注解驱动的 IoC 与 MVC：支持 @Controller、@Service、@Autowired、@RequestMapping 等，自动扫描装配。

动态代理 ORM：@Mapper + @Select 注解，运行时生成代理对象执行 SQL。

Pipeline 责任链：请求处理、代码分析、依赖分析均采用 Pipeline 模式，灵活扩展。

多线程代码分析：支持 GitIgnore 过滤、语言识别、复杂度估算、文件合并。

方法级调用链分析：手写 Java 词法分析器，构建方法调用图，生成 PlantUML 时序图。

AI Agent 多后端：集成 Ollama、DeepSeek、自定义 API、项目助手（知识库 + n-gram 降级）。

3D 可视化：基于 Three.js 和 ECharts GL，展示项目模块、调用链、实时监控等。

自研 SimpleVue：响应式数据、模板编译、组件化、样式隔离，前后端一体。

丰富的工具集：数据库浏览器、目录管理、脚本引擎、系统监控、题库分析、爬虫、截图助手。

九、许可证
MIT License © 2025 CodeStats Contributors

CodeStats — 从零构建企业级 Java 平台的绝佳学习项目，也是快速搭建开发工具平台的首选基础。
推荐给每一位想深入理解 Java Web 底层原理、又不想被庞大源码劝退的开发者。