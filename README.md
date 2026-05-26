<!-- ========== AI 生成内容标识（依据《人工智能生成合成内容标识办法》） ========== -->

# CodeStats – 综合性开发工具平台

>  **AI 辅助声明**  
> 本项目**大量使用 AI 辅助编程**（deepseek）。
> - 约 **100% 的代码**由 AI 生成初稿，经人工审查、调试、合并后形成最终版本。
> - AI 生成的代码主要集中在：**控制器、服务层、MyBatis 代理、前端 Vue 组件、配置文件、重复性 CRUD 逻辑**。
> - 人工贡献部分：**架构设计、核心 IoC/Tomcat 容器、依赖分析引擎、安全校验、项目助手知识库**。

> **透明性承诺**
> - 每个源代码文件的头部均包含 AI 生成标记（见下文）。
> - 每次 Git 提交中若代码由 AI 生成，提交信息会包含 `AI-assisted-by:` 签名。
> - 本仓库不包含任何训练数据或模型，仅发布最终可执行代码。

> **法律责任声明**  
> 根据《人工智能生成合成内容标识办法》，使用者应知晓本项目中部分内容可能由 AI 生成，不构成对原著作权或专利权的放弃。原作者保留对人工撰写部分的完整权利。



CodeStats – 自研全栈 Java 平台
完全从零构建的轻量级 Java Web 框架 + 多功能工具集
无需任何第三方框架（Tomcat/Spring/MyBatis），展示底层原理与实战能力。

📖 目录
项目简介

架构亮点

功能模块

快速开始

API 示例

技术栈

项目结构

贡献指南

许可证

项目简介
CodeStats 是一个教学级的 Java Web 平台，旨在演示如何从零实现：

🔥 嵌入式 Web 服务器（模拟 Tomcat 的 Connector、Pipeline、Container 体系）

🧩 IoC 容器（包扫描、依赖注入、Bean 生命周期）

🌐 MVC 框架（@Controller、@RequestMapping、参数绑定、@ResponseBody）

🗄️ 轻量 ORM（@Mapper 接口 + @Select 注解 + JDBC 代理）

📊 代码分析引擎（多线程统计行数、注释、语言识别、GitIgnore 过滤）

🤖 AI Agent 集成（支持 Ollama 本地模型、DeepSeek API、自定义 API）

📁 文件管理（目录树、文件编辑、上传下载、ZIP 打包）

🗃️ 数据库元数据浏览器（JDBC 连接、树形展示表/列）

📝 自研日志框架（Appender、Layout、异步、滚动文件）

🗂️ 缓存框架（内存缓存 + Caffeine 可选）

🎨 前端响应式框架（SimpleVue – 模拟 Vue 的响应式、模板编译、组件）

所有功能均为完全自研，可用于学习框架设计思想，也可作为快速开发的基础平台。

架构亮点
模块	核心实现
Web 容器	自研 Connector（BIO/NIO 线程池）、Pipeline-Value 责任链、WebappClassLoader
IoC 容器	SimpleApplicationContext 包扫描、@Autowired 注入，支持 @Controller/@Service/@Repository/@Component
MVC 核心	DispatcherServlet、HandlerMapping、ParamBinder（请求参数绑定）、@ResponseBody 自动 JSON
ORM 映射	@Mapper + @Select 动态代理，自动执行 SQL 并映射结果，支持超时设置
数据库连接池	阻塞队列 + 信号量（Semaphore）控制并发，支持空闲回收、连接验证
代码分析引擎	Pipeline 模式：CollectStage → ParallelAnalyzeStage → AggregateStage → OutputStage，支持 GitIgnore、多线程、多种输出格式
日志框架	LoggerFactory + Logger，支持 XML 配置、Appender（Console/File/RollingFile/Async）、PatternLayout、LevelRangeFilter
缓存框架	Cache 接口，实现 LocalCache（ConcurrentHashMap + TTL + 定期清理）和 CaffeineCache（可选）
SimpleVue 前端	响应式数据（Proxy + Dep + Watcher）、模板编译（{{}}、@click、v-model）、组件化、样式隔离（scoped CSS）
功能模块
1. 项目导航（首页）
展示平台简介、模块列表、技术栈、快速链接。

通过顶部 Tab 切换各功能模块（数据库、代码分析、目录管理、AI Agent）。

2. 数据库浏览器
连接 MySQL（通过 application.properties 配置），动态加载数据库、表、列信息。

树形菜单展示，点击表显示列属性（类型、可空、自增、备注等）。

预留数据视图和 ER 图扩展。

3. 代码分析
扫描指定目录，支持扩展名过滤、排除路径、.gitignore、额外文件/目录。

多线程并行分析每个文件的总行数、代码行、注释行、空行、语言、复杂度。

结果可按行数、文件名、语言、复杂度排序。

支持导出 CSV、合并所有文件内容为单个文本文件。

提供 Web 界面（/demo/code/codeStats.html）和 Swing GUI（CodeStatsGUI）以及命令行模式。

4. 目录管理
浏览本地或服务器目录（可配置根目录）。

文件/目录的创建、重命名、删除，文本文件在线编辑保存。

支持文件上传（Base64 编码传输）、单个文件下载、目录打包为 ZIP 下载。

树形导航 + 路径面包屑。

5. AI Agent
对接 Ollama（本地 DeepSeek 模型）：检查服务状态、拉取模型、运行测试。

对接 DeepSeek 官方 API（需配置 deepseek.api.key）。

支持自定义 API（任意兼容 OpenAI 格式的端点）。

Web 聊天界面，支持多轮对话、模型选择、温度调节。

提供 Playwright 自动化方案（可选）用于绕过前端限制。

6. 开源工具页（额外）
独立的 DeepSeek 对话页面，使用后端代理（/deepseek/api/chat）保持会话上下文。

7. 自研 SimpleVue 重构版（/face）
完全基于自研 Component + 响应式系统重写的前端界面。

展示组件化开发、模板语法、事件绑定、样式注入。

快速开始
环境要求
JDK 8+（推荐 JDK 11+）

Maven 3.6+（项目使用标准 Maven 结构）

MySQL 数据库（可选，用于数据库模块）

Ollama（可选，用于本地 AI 模型）

构建与运行
bash
# 克隆仓库
git clone https://github.com/your-repo/CodeStats.git
cd CodeStats

# 使用 Maven 编译
mvn clean package

# 运行主启动类（内置 Tomcat 服务器）
java -cp target/CodeStats-1.0.jar com.omni.run.Bootstrap
启动后访问：http://localhost:28080/codestats/ （端口和 context path 可在 application.properties 修改）

配置数据库（数据库模块）
在 src/main/resources/application.properties 中添加：

properties
db.url=jdbc:mysql://localhost:3306/yourdb?useSSL=false
db.username=root
db.password=123456
db.maxActive=10
配置 AI 模块（可选）
properties
# Ollama 地址（默认 http://localhost:11434）
ollama.api.url=http://localhost:11434/api/chat

# DeepSeek API Key
deepseek.api.key=sk-xxxxxx

# 自定义 API（可选）
custom.api.url=https://api.deepseek.com/v1/chat/completions
custom.api.key=sk-xxxxxx

# 启用 Playwright（高级自动化，需安装浏览器驱动）
playwright.enabled=false
API 示例
代码分析 – 启动分析并获取结果
bash
GET /code/api/analyze?rootDir=D:/project&extensions=java,js&excludeComments=true
返回 JSON，包含文件列表和汇总统计。

目录树 – 获取根目录
bash
GET /files/api/tree?rootType=local&path=
AI 聊天 – 发送消息
bash
POST /agent/api/chat
Content-Type: application/json

{
  "model": "deepseek-r1:7b",
  "messages": [{"role":"user","content":"Hello"}],
  "temperature": 0.7,
  "backend": "ollama"
}
技术栈
领域	自研实现	可选的第三方库
Web 服务器	Connector + Pipeline + Servlet 容器	-
IoC 容器	注解扫描 + 反射注入 + 单例池	-
ORM	MapperProxy + JdbcTemplate + 注解 SQL	-
连接池	阻塞队列 + Semaphore + 连接验证	-
日志	Appender + Layout + XML 配置	-
缓存	ConcurrentHashMap + TTL + 定期清理	Caffeine
代码分析	Pipeline + 多线程 + 语言识别 + GitIgnore	SnakeYAML (解析配置)
AI 集成	HttpClient + JSON 解析	Playwright (可禁用)
前端	SimpleVue (自研响应式+组件)	原生 JavaScript / CSS
项目结构
text
CodeStats/
├── src/main/java/com/codestats/
│   ├── Bootstrap.java                 # 主入口
│   ├── business/assistance/           # 业务模块（agent, codestat, directory, table）
│   ├── common/                        # 公共组件（cache, util）
│   ├── framework/                     # 自研框架核心
│   │   ├── codestats/                 # 代码分析引擎
│   │   ├── jdbc/                      # JDBC 工具 + 元数据服务
│   │   ├── log/                       # 日志框架
│   │   ├── mybatis/                   # Mapper 代理实现
│   │   ├── pool/                      # 连接池
│   │   ├── spring/                    # IoC + MVC
│   │   ├── tomcat/                    # 嵌入式 Web 容器
│   │   ├── util/                      # 工具类（JSON、参数解析）
│   │   └── v4j/                       # SimpleVue 前后端框架
│   └── run/                           # 演示入口（CacheDemo, LogDemo, VFJAppDemo）
├── webapps/                           # Web 应用静态资源
│   ├── demo/                          # 示例页面（HTML/CSS/JS）
│   ├── face/                          # SimpleVue 重构版前端
│   └── vue/                           # SimpleVue 核心库
└── pom.xml
贡献指南
欢迎提交 issue 和 pull request。可参与的方向：

完善单元测试

增加更多语言支持（代码分析模块）

实现 WebSocket 支持（当前已有预留）

优化前端 UI（科技感主题可进一步打磨）

编写更详细的自研框架文档

许可证
MIT License © 2025 CodeStats Contributors

CodeStats – 探索从零构建企业级 Java 平台的乐趣 🚀