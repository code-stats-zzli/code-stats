#你的 Star 能让更多开发者

CodeStats – 从零构建的 Java 全栈平台
不依赖 Tomcat、不依赖 Spring、不依赖 MyBatis

从 Socket 开始，手写 Web 服务器、IoC 容器、MVC 框架、ORM 映射、连接池、日志框架、缓存框架——全部自研。

一分钟快速启动
bash
# 下载最新版本
wget https://github.com/your-repo/CodeStats/releases/download/latest/CodeStats-1.0.zip

# 解压运行
unzip CodeStats-1.0.zip && cd CodeStats
java -jar CodeStats-1.0.jar

# 浏览器访问
http://localhost:28080/
无需任何配置：不配置数据库，核心功能（代码分析、文件管理、AI Agent）完整可用。

核心亮点
模块	自研实现	说明
Web 容器	BIO/NIO 线程池 + Pipeline-Valve 责任链	模拟 Tomcat 架构
IoC 容器	包扫描 + 反射注入 + 单例池	@Controller/@Service/@Autowired
MVC 框架	DispatcherServlet + HandlerMapping + 参数绑定	@RequestMapping/@ResponseBody
ORM 框架	MapperProxy 动态代理 + 注解 SQL	@Mapper/@Select，自动映射结果
连接池	阻塞队列 + Semaphore 并发控制	支持空闲回收、连接验证
日志框架	Appender + Layout + XML 配置	异步/滚动文件/级别过滤
缓存框架	ConcurrentHashMap + TTL + 定期清理	可选 Caffeine 扩展
前端框架	Proxy + Dep + Watcher 响应式系统	SimpleVue - 自研类 Vue 框架
功能矩阵
代码分析引擎

多线程并行统计：总行数、代码行、注释行、空行、复杂度

支持 GitIgnore 过滤、扩展名筛选、排除路径

结果排序 + CSV 导出 + 全文合并

提供 Web 界面 / Swing GUI / 命令行 三种模式

目录管理

树形导航 + 面包屑路径

文件/目录创建、重命名、删除

文本文件在线编辑保存

文件上传、目录打包 ZIP 下载

AI Agent

Ollama 本地模型：检查服务状态、拉取模型、运行测试

DeepSeek API：官方接口对接

自定义 API：兼容 OpenAI 格式的任意端点

内置知识库：不配置任何 Key 时，仍可使用"项目助手"

数据库浏览器（可选）

连接 MySQL，动态加载数据库/表/列信息

树形菜单展示，查看列属性

不配置数据库时自动提示，不影响其他功能

SimpleVue 前端重构版

自研响应式系统 + 组件化架构

模板编译、事件绑定、样式隔离

架构一览
text
CodeStats 自研架构

Web层    自研Connector + Pipeline-Valve + WebappClassLoader
IoC层    包扫描 + Bean生命周期 + @Autowired + 单例池
MVC层    DispatcherServlet + HandlerMapping + 参数绑定
ORM层    MapperProxy代理 + @Select注解 + JdbcTemplate
中间件   连接池 + 日志框架 + 缓存框架 + SimpleVue前端
业务层   代码分析 + 目录管理 + AI Agent + 数据库浏览器
项目结构
text
CodeStats/
├── framework/
│   ├── tomcat/        自研Web容器
│   ├── spring/        自研IoC + MVC
│   ├── mybatis/       自研ORM
│   ├── jdbc/          JDBC工具
│   ├── pool/          自研连接池
│   ├── log/           自研日志框架
│   ├── cache/         自研缓存
│   └── v4j/           SimpleVue前端框架
├── business/          业务模块
├── webapps/           静态资源
└── Bootstrap.java     主入口
技术细节速览
Web 容器实现

从 ServerSocket.accept() 开始解析 HTTP 请求

自定义 Connector（BIO/NIO 线程池）

Pipeline-Valve 责任链处理请求

自研 WebappClassLoader 隔离应用

IoC 容器核心

扫描 @Component 注解的类

递归注入 @Autowired 依赖

三级缓存解决循环依赖

ORM 原理

@Mapper 接口动态代理

@Select 注解提取 SQL

JDBC 执行 + 反射映射结果集

连接池机制

阻塞队列管理空闲连接

Semaphore 控制并发数

空闲连接回收 + 连接有效性验证

代码分析 Pipeline

Collect → ParallelAnalyze → Aggregate → Output

多线程并行处理文件

GitIgnore 模式匹配过滤

API 示例
text
GET /code/api/analyze?rootDir=/project&extensions=java,js
GET /files/api/tree?rootType=local&path=/home
POST /agent/api/chat
为什么值得一看？
教学级源码：每个模块都是简化但完整的实现，适合学习底层原理

开箱即用：下载运行，无需数据库也能体验全部核心功能

真实可用：代码分析、AI 对话、文件管理都已投入日常使用

持续完善：从 Web 容器到前端框架，真正意义上的全栈自研

如果这个项目对你有帮助，欢迎点个 Star 支持一下

你的 Star 能让更多开发者看到这份从零构建的 Java 全栈实践

立即体验

text
git clone https://github.com/your-repo/CodeStats.git
cd CodeStats
mvn clean package
java -jar target/CodeStats-1.0.jar
探索从零构建企业级 Java 平台的乐趣。