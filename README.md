CodeStats – 自研全栈 Java 平台
完全从零构建的轻量级 Java Web 框架 + 多功能工具集
无需任何第三方框架（Tomcat/Spring/MyBatis），展示底层原理与实战能力。
下载即用，无需复杂配置，不依赖数据库也可运行核心功能。

🚀 最简启动（推荐）
方式一：下载最新启动包（ZIP）
下载 CodeStats-latest.zip（或本地打包后的 target/CodeStats-1.0.zip）

解压到任意目录

双击运行 start.bat（Windows）或 ./start.sh（Linux/Mac）

浏览器打开：http://localhost:28080/

方式二：从源码构建
bash
git clone https://github.com/your-repo/CodeStats.git
cd CodeStats
mvn clean package
java -jar target/CodeStats-1.0.jar
💡 无需任何配置！

不配置数据库，代码分析、目录管理、AI Agent、项目导航等核心功能依然完整可用

数据库模块（表浏览）为可选功能，需要时再配置 application.properties

📖 项目简介
CodeStats 是一个教学级的 Java Web 平台，演示如何从零实现：

🔥 嵌入式 Web 服务器（模拟 Tomcat 的 Connector、Pipeline、Container 体系）

🧩 IoC 容器（包扫描、依赖注入、Bean 生命周期）

🌐 MVC 框架（@Controller、@RequestMapping、参数绑定、@ResponseBody）

🗄️ 轻量 ORM（@Mapper 接口 + @Select 注解 + JDBC 动态代理）

📊 代码分析引擎（多线程统计行数、注释、语言识别、GitIgnore 过滤）

🤖 AI Agent 集成（支持 Ollama 本地模型、DeepSeek API、自定义 API）

📁 文件管理（目录树、文件编辑、上传下载、ZIP 打包）

🗃️ 数据库元数据浏览器（JDBC 连接、树形展示表/列）

📝 自研日志框架（Appender、Layout、异步、滚动文件）

🗂️ 缓存框架（内存缓存 + Caffeine 可选）

🎨 前端响应式框架（SimpleVue – 模拟 Vue 的响应式、模板编译、组件）

✨ 架构亮点
模块	核心实现
Web 容器	自研 Connector（BIO/NIO 线程池）、Pipeline-Valve 责任链、WebappClassLoader
IoC 容器	包扫描、注解驱动、@Autowired 注入，支持 @Controller/@Service/@Repository
MVC 核心	DispatcherServlet、HandlerMapping、ParamBinder（请求参数绑定）、@ResponseBody 自动 JSON
ORM 映射	@Mapper + @Select 动态代理，自动执行 SQL 并映射结果，支持超时设置
数据库连接池	阻塞队列 + 信号量（Semaphore）控制并发，支持空闲回收、连接验证
代码分析引擎	Pipeline 模式：CollectStage → ParallelAnalyzeStage → AggregateStage → OutputStage，支持 GitIgnore、多线程、多种输出格式
日志框架	LoggerFactory + Logger，支持 XML 配置，多种 Appender（Console/File/RollingFile/Async）、PatternLayout、LevelRangeFilter
缓存框架	Cache 接口，实现 LocalCache（ConcurrentHashMap + TTL + 定期清理）和 CaffeineCache（可选）
SimpleVue 前端	响应式数据（Proxy + Dep + Watcher）、模板编译（{{}}、@click、v-model）、组件化、样式隔离（scoped CSS）
🧩 功能模块
1. 项目导航（首页）
展示平台简介、模块列表、技术栈、快速链接

通过顶部 Tab 切换各功能模块

2. 数据库浏览器（可选）
连接 MySQL（需配置），动态加载数据库、表、列信息

树形菜单展示，点击表显示列属性（类型、可空、自增、备注等）

若不配置数据库，该模块自动提示，不影响其他功能

3. 代码分析
扫描指定目录，支持扩展名过滤、排除路径、.gitignore、额外文件/目录

多线程并行分析总行数、代码行、注释行、空行、语言、复杂度

结果排序（行数/文件名/语言/复杂度）、导出 CSV、合并所有文件内容为单个文本文件

提供 Web 界面、Swing GUI、命令行三种模式

4. 目录管理
浏览本地或服务器目录（可配置根目录）

文件/目录的创建、重命名、删除，文本文件在线编辑保存

支持文件上传（Base64）、单个文件下载、目录打包为 ZIP 下载

树形导航 + 路径面包屑

5. AI Agent
对接 Ollama（本地 DeepSeek 模型）：检查服务状态、拉取模型、运行测试

对接 DeepSeek 官方 API（需配置 deepseek.api.key）

支持自定义 API（任意兼容 OpenAI 格式的端点）

Web 聊天界面，支持多轮对话、模型选择、温度调节

不配置任何 API Key 时，仍可使用“项目助手”内置知识库回答问题

6. 开源工具页（额外）
独立的 DeepSeek 对话页面，使用后端代理保持会话上下文

7. 自研 SimpleVue 重构版（/face）
完全基于自研 Component + 响应式系统重写的前端界面

展示组件化开发、模板语法、事件绑定、样式注入

⚙️ 可选配置（非必须）
若需要使用数据库浏览器或 DeepSeek 官方 API，可在 src/main/resources/application.properties 中添加：

properties
# 数据库连接（可选）
db.url=jdbc:mysql://localhost:3306/yourdb?useSSL=false
db.username=root
db.password=123456
db.maxActive=10

# AI 配置（可选）
deepseek.api.key=sk-xxxxxx
ollama.api.url=http://localhost:11434/api/chat
不配置上述任何内容，项目依然可以正常运行，只是相应功能模块会提示“未配置”。

📡 API 示例
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
🛠️ 技术栈
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
📁 项目结构（简化）
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
│   │   └── v4j/                       # SimpleVue 前后端框架
│   └── run/                           # 演示入口
├── webapps/                           # Web 应用静态资源
│   ├── demo/                          # 示例页面（HTML/CSS/JS）
│   ├── face/                          # SimpleVue 重构版前端
│   └── vue/                           # SimpleVue 核心库
└── pom.xml
🤝 贡献指南
欢迎提交 issue 和 pull request。可参与的方向：

完善单元测试

增加更多语言支持（代码分析模块）

实现 WebSocket 支持（当前已有预留）

优化前端 UI（科技感主题可进一步打磨）

编写更详细的自研框架文档

📄 许可证
MIT License © 2026 CodeStats Contributors

CodeStats – 探索从零构建企业级 Java 平台的乐趣 🚀