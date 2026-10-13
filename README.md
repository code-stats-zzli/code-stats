CodeStats
一个教学级 Java Web 框架 Omni + 一个功能演示平台 CodeStats

不依赖 Tomcat、不依赖 Spring、不依赖 MyBatis —— 从 Socket 开始，手写 Web 服务器、IoC 容器、MVC 框架、ORM 映射、连接池、日志框架、缓存框架，全部自研。

Omni 用来教框架原理，CodeStats 用来演示框架能力。

https://img.shields.io/badge/license-MIT-blue.svg
https://img.shields.io/badge/Java-8%2B-orange.svg
https://img.shields.io/badge/build-maven-green.svg

两个仓库
仓库	地址	定位
Omni 框架	https://gitee.com/zhouzuoli/omni-lab.git	教学级框架 —— 从零实现，代码可读，适合精读
CodeStats 应用	https://gitee.com/zhouzuoli/code-stats.git	功能演示平台 —— 用 Omni 撑起一个真实系统
Omni 证明"框架能跑"，CodeStats 证明"框架能用"。

一句话定位
Omni = 一个能读完的迷你 Spring + Tomcat + MyBatis
CodeStats = 一个能跑起来的全栈工具平台，用来演示 Omni 的实际能力

为什么做这个项目
市面上讲 Java Web 底层原理的资料，大多有两个问题：

太浅：只写一个 IoC 容器就结束，看不到完整链路

太深：直接读 Spring / Tomcat 源码，几百万行，劝退

Omni 想填这个空白：用能读完的代码，实现完整的核心链路，从 ServerSocket.accept() 到 Controller 返回 JSON，每一步都看得见。

CodeStats 想证明：这个框架不是玩具，它能撑起一个包含十几个模块的真实应用。

AI 辅助声明
本项目大量使用 AI 辅助编程（DeepSeek）。

约 100% 代码由 AI 生成初稿，经人工审查、调试、合并

AI 生成集中在：Controller、Service、Mapper 代理、前端组件、配置、重复 CRUD

人工贡献集中在：架构设计、核心 IoC/Tomcat 容器、依赖分析引擎、安全校验、项目助手知识库

依据《人工智能生成合成内容标识办法》，使用者应知晓部分内容可能由 AI 生成

一分钟快速启动
bash
# 1. 克隆项目
git clone https://gitee.com/zhouzuoli/code-stats.git
cd code-stats

# 2. 编译打包
mvn clean package

# 3. 启动（内置 Web 服务器）
java -jar target/code-stats-1.7.1.jar
浏览器访问：http://localhost:28080/omni/demo/index.html

无需任何配置：不配置数据库，代码分析、目录管理、AI Agent 等核心功能完整可用。

两层结构
text
┌──────────────────────────────────────────────────────┐
│          CodeStats (功能演示平台)                     │
│  用真实模块演示 Omni 框架的能力                       │
│  代码分析 · 目录管理 · AI Agent · 数据库 · 3D 可视化  │
│  自动化测试 · API 调试 · 系统监控 · 脚本引擎 · 爬虫   │
└──────────────────────┬───────────────────────────────┘
                       │ 基于
┌──────────────────────▼───────────────────────────────┐
│            Omni (教学级框架)                          │
│  用可读代码演示企业级框架的核心原理                    │
│  Mini Tomcat · Mini Spring · Mini MVC · Mini MyBatis │
│  连接池 · 日志 · 缓存 · 表达式引擎 · 脚本引擎         │
└──────────────────────┬───────────────────────────────┘
                       │ 从零实现
┌──────────────────────▼───────────────────────────────┐
│              JDK 8+ · Socket · JDBC                  │
└──────────────────────────────────────────────────────┘
Omni：教学级框架
设计目标
代码可读：每个类都有清晰职责，注释完整，不炫技

链路完整：从 Socket 到 Controller，每一步都能追踪

零依赖：核心模块不依赖任何第三方框架

可运行：不是伪代码，是能跑真实 Web 应用的最小框架

核心模块
模块	教学内容	关键类
Mini Tomcat	HTTP 解析、线程池、责任链、类加载	Connector / Pipeline / Request / Response
Mini Spring	包扫描、Bean 生命周期、依赖注入	AnnotationConfigApplicationContext / BeanFactory
Mini MVC	路由匹配、参数绑定、JSON 序列化	DispatcherServlet / ParamBinder
Mini MyBatis	动态代理、注解 SQL、结果映射	MapperProxy / JdbcTemplate
连接池	信号量、阻塞队列、连接验证	SimpleDataSource / PooledConnection
日志框架	Appender / Layout / Filter	LoggerFactory / PatternLayout
缓存框架	TTL、惰性删除、定期清理	LocalCache
表达式引擎	词法分析、递归下降、AST	Lexer / StandardExpressionParser
脚本引擎	进程调用、动态编译	BaseScriptExecutor / JavaExecutor
教学亮点：一个请求的完整链路
text
1. Client 发起 HTTP 请求
2. Connector.accept() 接收 Socket
3. Request.parse() 解析 HTTP 报文
4. Pipeline.invoke() 责任链流转
5. DispatcherServlet 匹配 Handler
6. ParamBinder 绑定参数
7. 反射调用 Controller 方法
8. Service → MapperProxy → JdbcTemplate
9. SimpleDataSource 获取连接
10. ResultSet → Bean 映射
11. @ResponseBody 序列化为 JSON
12. Response.send() 写回客户端
这 12 步，每一步都有对应源码，可以逐个打断点追踪。

框架核心注解
java
@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @RequestMapping("/info")
    @ResponseBody
    public User info(@RequestParam("id") int id) {
        return userService.findById(id);
    }
}

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public User findById(int id) {
        return userMapper.selectById(id);
    }
}

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectById(@Param("id") int id);
}
这些注解全部由 Omni 实现，不依赖 Spring / MyBatis。

CodeStats：功能演示平台
设计目标
演示框架能力：用真实模块证明 Omni 能支撑复杂系统

开箱即用：不配置数据库也能跑核心功能

模块可插拔：每个模块独立，可按需裁剪

全栈覆盖：后端 + 前端 + 3D + AI，展示框架的边界

功能模块
模块	演示了什么框架能力
代码分析	Pipeline 责任链、多线程、文件 IO
目录管理	MVC 参数绑定、文件流、安全校验
AI Agent	HTTP 客户端、会话管理、多后端适配
数据库浏览器	ORM 映射、元数据、动态数据源
API 调试	注解扫描、反射、API 元数据生成
自动化测试	Pipeline 执行、变量解析、断言
系统监控	组件化架构、JMX、实时数据采集
3D 可视化	前后端分离、WebSocket 风格数据推送
脚本引擎	进程调用、动态编译、类加载
题库分析	Excel 解析、缓存、OCR
网页爬虫	并发、限速、状态回调
SimpleVue 前端	自研前端框架，响应式 + 组件化
演示亮点
代码分析：

多线程并行统计总行数、代码行、注释行、空行、复杂度

GitIgnore 过滤 + 扩展名筛选 + 排除路径

方法级调用链：手写 Java 词法分析器 → 调用图 → PlantUML 时序图

循环依赖检测：Tarjan 强连通分量算法

AI Agent：

Ollama 本地模型 / DeepSeek API / 自定义 API / 项目助手（知识库 + n-gram 降级）

不配置任何 Key 时，仍可使用内置知识库

数据库浏览器：

多数据源管理 + 驱动上传

元数据浏览 + 表数据分页 + SQL 查询

可视化视图：拖拽表 → 字段转换 → 生成 SQL

3D 可视化：

依赖图 / 调用链 / 实时监控 / 方法级调用树

配置化场景引擎（JSON 驱动）

项目结构
text
code-stats/
├── src/main/java/com/omni/
│   ├── Bootstrap.java              # 启动入口
│   ├── framework/                  # ===== Omni 框架层（教学级）=====
│   │   ├── tomcat/                 # Mini Tomcat
│   │   ├── spring/                 # Mini Spring + MVC
│   │   ├── mybatis/                # Mini MyBatis
│   │   ├── jdbc/                   # JdbcTemplate
│   │   ├── pool/                   # 连接池
│   │   ├── log/                    # 日志框架
│   │   ├── cache/                  # 缓存框架
│   │   ├── expression/             # 表达式引擎
│   │   ├── scriptexec/             # 脚本执行引擎
│   │   ├── swag/                   # Swagger 风格 API
│   │   ├── jstats/                 # JVM 监控核心
│   │   └── v4j/                    # SimpleVue 后端支持
│   ├── common/                     # 公共工具
│   └── business/assistance/        # ===== CodeStats 应用层（演示）=====
│       ├── codestat/               # 代码分析
│       ├── directory/              # 目录管理
│       ├── agent/                  # AI Agent
│       ├── table/mysql/            # 数据库浏览器
│       ├── apiexplorer/            # API 调试
│       ├── autotest/               # 自动化测试
│       ├── systemmonitor/          # 系统监控
│       ├── three3d/                # 3D 可视化
│       ├── scriptengine/           # 脚本引擎
│       ├── questionbank/           # 题库分析
│       ├── spider/                 # 网页爬虫
│       ├── jstats/                 # JVM 监控
│       ├── screenshot/             # 截图助手
│       ├── introduction/           # 项目介绍
│       └── datasource/             # 数据源管理
├── src/main/resources/
│   ├── application.properties      # 应用配置
│   ├── config.yaml                 # 模块配置
│   ├── log-config.xml              # 日志配置
│   └── project_knowledge*.json     # 项目助手知识库
├── webapps/
│   ├── demo/                       # 完整 Web 界面
│   ├── face/                       # SimpleVue 重构版
│   └── vue/                        # SimpleVue 核心库
└── pom.xml
核心机制速览
Web 容器（Mini Tomcat）
text
ServerSocket.accept()
  → Request 解析 HTTP 报文
  → Pipeline 责任链：Engine → Host → Context → Wrapper
  → Servlet.service()
IoC 容器（Mini Spring）
text
包扫描 → BeanDefinition 注册 → 反射实例化 → @Autowired 注入 → 单例池
ORM 映射（Mini MyBatis）
text
@Mapper 接口 → JDK 动态代理 → 解析 @Select 注解
  → #{xxx} 替换为 ? → 绑定参数 → ResultSet → Bean
连接池
text
Semaphore 控制并发 + ArrayBlockingQueue 存放空闲连接
  → 获取：acquire → 队列取或新建
  → 归还：验证 → 放回 → release
代码分析 Pipeline
text
Collect → ParallelAnalyze → Aggregate → Output
  多线程并行 + GitIgnore 过滤 + 多格式输出
配置（可选）
在 src/main/resources/application.properties 中：

properties
# 服务器端口
server.port=28080
context.path=/omni

# 数据库连接（可选）
db.url=jdbc:mysql://localhost:3306/demo?useSSL=false&serverTimezone=UTC
db.username=root
db.password=123456
db.maxActive=20

# AI 配置（可选）
ollama.api.url=http://localhost:11434/api/chat
deepseek.api.key=sk-xxxxxx

# 包扫描路径
scan.packages=com.omni.**.controller,com.omni.**.service,com.omni.**.mapper

# DispatcherServlet 全限定类名
servlet.class=com.omni.framework.spring.mvc.DispatcherServlet
不配置上述任何内容，项目依然可以正常运行。

API 示例
text
# 代码分析
GET /code/api/analyze?rootDir=/project&extensions=java,js&excludeComments=true

# 目录树
GET /files/api/tree?rootType=local&path=

# AI 聊天
POST /agent/api/chat
Content-Type: application/json

{
  "model": "deepseek-r1:7b",
  "messages": [{"role": "user", "content": "Hello"}],
  "temperature": 0.7,
  "backend": "ollama"
}
技术栈
领域	自研实现	可选第三方
Web 服务器	Connector + Pipeline + Servlet	—
IoC 容器	注解扫描 + 反射注入	—
MVC 框架	DispatcherServlet + ParamBinder	—
ORM	MapperProxy + JdbcTemplate	—
连接池	Semaphore + BlockingQueue	—
日志	Appender + Layout + XML	—
缓存	ConcurrentHashMap + TTL	Caffeine
表达式引擎	Lexer + Parser	—
脚本引擎	Process + JavaCompiler API	—
JSON	反射 + 手写序列化	Jackson
YAML 解析	—	SnakeYAML
HTTP 客户端	HttpURLConnection	Apache HttpClient
前端框架	SimpleVue（响应式 + 组件）	—
推荐人群
人群	从哪看起
Java 初中级开发者	从 Omni 的 IoC 和 Mini Tomcat 入手，理解 Spring / Tomcat 底层
计算机专业学生 / 应届生	精读框架层，作为校招项目经验；CodeStats 作为功能演示
全栈学习者	Omni 后端 + SimpleVue 前端，一站式学习框架设计
AI 集成开发者	看 CodeStats 的 Agent 模块：多后端 + 知识库 + 降级
工具平台开发者	直接基于 CodeStats 二次开发
JVM / 3D 可视化爱好者	看 CodeStats 的 3D 可视化模块
学习路径建议
text
第 1 步：跑起来
  → mvn clean package && java -jar target/code-stats-1.7.1.jar
  → 打开浏览器，体验各个模块

第 2 步：看框架
  → 从 Bootstrap 进入 → SpringApplication → AnnotationConfigApplicationContext
  → 理解 IoC 容器如何启动

第 3 步：追请求
  → 从 Connector.accept() 开始 → Request.parse() → Pipeline → DispatcherServlet
  → 追踪一个完整的 HTTP 请求链路

第 4 步：读模块
  → 挑一个感兴趣的模块（代码分析 / AI Agent / 数据库）
  → 看它如何调用框架能力

第 5 步：改代码
  → 加一个自己的 @Controller
  → 加一个自己的 @Mapper
  → 感受框架的扩展方式
贡献指南
欢迎提交 Issue 和 Pull Request。可参与的方向：

补充单元测试

增加更多注解（@PostConstruct、@Value、@Configuration）

支持 WebSocket

优化 Pipeline 责任链性能

完善教学文档

开发规范
代码风格：Alibaba Java Coding Guidelines

提交信息：feat: / fix: / docs: / refactor: / test:

提交前：mvn clean test

许可证
MIT License © 2025 CodeStats Contributors

相关链接
项目	地址	定位
Omni 框架	https://gitee.com/zhouzuoli/omni-lab.git	教学级框架
CodeStats 应用	https://gitee.com/zhouzuoli/code-stats.git	功能演示平台
问题反馈	Issues	—
Omni —— 一个能读完的迷你 Spring + Tomcat + MyBatis 🎓
CodeStats —— 一个能跑起来的全栈工具平台 🚀

如果这个项目对你有帮助，欢迎点个 Star 支持一下。