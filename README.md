# CodeStats

CodeStats 是一个完全自研的轻量级 Java Web 平台，底层基于迷你 Tomcat 和 IoC 容器，展示了从零构建企业级框架的核心技术。

📦 分层架构
表现层：HTML5/CSS3/JS 前端 + WebSocket
Web 容器层：自研 Connector（NIO 线程池）、Pipeline-Valve 责任链、WebappClassLoader
MVC 核心：DispatcherServlet、HandlerMapping、参数解析、视图解析
业务层：Controller / Service / Mapper 注解驱动
基础设施层：JDBC 连接池、JdbcTemplate、MyBatis 风格代理、本地缓存（Caffeine）
⚙️ 核心流程
请求到达 → Connector 解析 → Pipeline 流转 → DispatcherServlet 匹配 Handler → 执行 Controller → 返回 JSON/HTML。

🔧 技术亮点
完全自研 IoC 容器，支持 @Autowired 注入
模拟 Tomcat 类加载隔离，支持 Web 应用热部署
JDBC 连接池：阻塞队列 + Semaphore 限流，支持空闲连接回收
代码分析引擎：Pipeline 模式，多线程并行处理，支持 GitIgnore 过滤

CodeStats 项目综合评估报告
一、项目定位与功能完整性
定位：
CodeStats 是一个自研全栈开发工具平台，集成了代码统计分析、数据库客户端、文件目录管理、AI 助手（Agent）、项目介绍导航等功能。其底层 Web 容器、IoC 容器、MVC 框架、JDBC 连接池、MyBatis 风格的 Mapper 代理均为自主实现，前端采用自研的 SimpleVue 响应式框架。

功能模块：

模块	功能描述	完成度
轻量 Web 框架	内置 Tomcat 风格的 Connector/Pipeline，IoC 容器，注解驱动的 MVC	核心完整，可运行
代码分析	多线程递归扫描文件，统计行数/代码行/注释行/空行，支持语言识别、排除路径、GitIgnore、结果导出 JSON/CSV，GUI 与命令行模式	功能较完备
数据库客户端	基于 JDBC MetaData 浏览数据库、表、列信息，树形导航	基础浏览可用，数据视图/ER 图未实现
文件目录管理	服务端文件浏览、编辑、上传、下载、重命名、删除，支持多根目录（本地/服务器），目录打包 ZIP	功能完整，有安全路径校验
AI Agent	集成 Ollama 本地模型管理（状态检查、拉取、运行）以及多后端聊天（Ollama/DeepSeek API/自定义 API），支持连续对话	核心 API 已实现，前端聊天界面可用
项目介绍	展示模块导航与详细说明（静态 HTML 后端拼接）	完成
评价：
功能覆盖面广，几乎覆盖了一个开发辅助平台的常见需求。但部分模块（如数据库数据视图、ER 图）仅为占位，未完成；AI Agent 的 Playwright 自动化方式不稳定且容易被反爬，实用性存疑。

二、技术架构与实现水准
后端架构亮点
自研 Web 容器：实现了 Connector（BIO 线程池）、Pipeline-Valve 责任链、Context 隔离的 WebappClassLoader，模仿 Tomcat 的容器层级（Server → Service → Engine → Host → Context → Wrapper），设计意图清晰。

IoC 容器：SimpleApplicationContext 支持包扫描，识别 @Controller/@Service/@Repository/@Component/@Mapper 注解，实现依赖注入（@Autowired），并自动为 @Mapper 接口生成 JDK 动态代理。

数据访问：

自研连接池 SimpleDataSource（基于 Semaphore + BlockingQueue），支持最大活跃数、空闲连接回收、连接校验。

JdbcTemplate 支持查询超时。

@Select 注解 + MapperProxy 执行 SQL，类似 MyBatis。

代码分析引擎：采用 Pipeline 模式（CollectStage → ParallelAnalyzeStage → AggregateStage → OutputStage），支持多线程并发分析，过滤器链（扩展名、排除路径、GitIgnore），语言识别与注释排除逻辑较细致。

日志框架：自研 Logger，支持 XML 配置、Appender（Console/File/RollingFile/Async）、布局模式、过滤器，占位符 {} 参数化已实现。

缓存抽象：提供 Cache 接口，实现基于 ConcurrentHashMap 的本地缓存（支持 TTL、定期清理），并可扩展 Caffeine。

前端实现
采用自研 SimpleVue 框架（基于 Proxy 实现响应式，Compiler 解析模板指令），代码量精简，展现了框架设计能力。

页面风格统一（科技感玻璃效果），使用 iframe 嵌入子模块，全局 apiUrl 处理 context-path。

不足与隐患
Web 容器稳定性：

Connector 使用 ServerSocket 每请求新建线程（虽然线程池），未实现 NIO 或非阻塞 IO，高并发下性能差。

SimplePipeline 中的路由逻辑混合在 ValveChainImpl 中，代码可读性较差。

WebappClassLoader 仅从 WEB-INF/classes 和 WEB-INF/lib 加载，未实现热部署、JSP 支持等。

安全性：

AgentController 直接执行系统命令 ollama pull，若模型名称被注入（如 deepseek-r1:7b && rm -rf /）存在命令注入风险（虽然前端限制了输入，但后端未做净化）。

文件下载接口对路径做了 Base64+URL 解码及 .. 检查，但仍有通过符号链接越权的可能。

未实现任何身份认证/鉴权机制，所有 API 开放。

代码质量：

部分类存在重复代码（如多个 Controller 中重复的 parseJson 方法）。

异常处理不一致（有的地方打印 e.printStackTrace()，有的用日志）。

硬编码较多（如 DatabaseClientController 中表属性引擎、字符集写死为 InnoDB/utf8mb4）。

单元测试缺失。

依赖管理：

使用了 snakeyaml、jackson-databind、playwright 等第三方库，但项目中未见明确的 pom.xml 或 build.gradle（可能未提供）。

部分 util 类（如 GitIgnoreLoader）在 lib.codestats 和 common.util 下重复存在。

三、代码专业水准评估
维度	评分（1-10）	说明
架构设计	7	模仿主流框架分层合理，Pipeline、过滤器链、容器模型有设计感，但部分实现粗糙。
代码规范	6	命名基本规范，但存在拼写错误（如 AbatractDatabaseMetadataService 应为 Abstract），注释不完整，包结构稍乱。
可维护性	5	模块间耦合度较高（如业务代码直接使用 com.framework 内部类），缺乏接口隔离，改动风险大。
健壮性	4	大量异常被吞没或仅打印，资源释放不严谨（如 Response 中的 headers.clear() 在异常时可能不执行），并发安全存疑。
创新性	7	完全自研 IoC 容器和 Web 服务器，体现了对底层原理的理解，比多数“学习项目”更完整。
文档	3	几乎没有 JavaDoc 或设计文档，前端代码无注释，新人接手困难。
综合水准：这是一个优秀的个人/团队学习项目，展示了对 Java Web 技术栈（Tomcat、Spring、MyBatis、连接池、日志框架）的深度实践，达到了中级偏上的开发水准。但它不适合直接用于生产环境，因为安全、性能、稳定性等方面存在明显短板。

四、实用性评估
作为代码统计工具：功能完整，支持多线程、多种语言、合并输出，GUI 和命令行均可用，具备实用价值。

作为数据库客户端：仅能浏览元数据，无法执行 SQL 或查看表数据，实用性较弱。

作为文件管理器：功能较全，可用于简单的远程文件管理，但无权限控制和操作审计。

作为 AI 助手：依赖本地 Ollama 或外部 API，聊天功能可用，但 Playwright 自动化访问 DeepSeek 官网的方式极易失效，且未实现流式输出，实用性一般。

作为自研框架演示：对学习 Java 底层技术很有帮助，可以作为一个教学案例。

总体实用性评分：6/10 —— 代码分析模块实用价值较高，其他模块多为技术演示，生产场景下建议使用成熟替代品。

五、总结
CodeStats 项目体量庞大、技术覆盖广，作者显然投入了大量精力，展现了对 Java 生态核心组件的深入理解。其最大价值在于学习与展示：通过实际代码演示了如何从零构建 IoC 容器、Web 服务器、ORM 映射、Pipeline 处理链等。

但从专业工程角度看，代码仍处于“原型系统”阶段，存在安全漏洞、性能瓶颈、代码冗余、测试缺失等问题。若目标是完善为一个可交付的产品，还需要补充：

全面的输入校验与权限控制

单元测试与集成测试

性能优化（NIO、连接池调优）

配置外部化与模块解耦

完整的文档与部署指南

最终定级：中等偏上水准的学习型综合项目，可作为简历中的亮点展示个人技术广度与深度，但不建议直接用于商业或生产环境。


#### 特技

1.  使用 Readme\_XXX.md 来支持不同的语言，例如 Readme\_en.md, Readme\_zh.md
2.  Gitee 官方博客 [blog.gitee.com](https://blog.gitee.com)
3.  你可以 [https://gitee.com/explore](https://gitee.com/explore) 这个地址来了解 Gitee 上的优秀开源项目
4.  [GVP](https://gitee.com/gvp) 全称是 Gitee 最有价值开源项目，是综合评定出的优秀开源项目
5.  Gitee 官方提供的使用手册 [https://gitee.com/help](https://gitee.com/help)
6.  Gitee 封面人物是一档用来展示 Gitee 会员风采的栏目 [https://gitee.com/gitee-stars/](https://gitee.com/gitee-stars/)
