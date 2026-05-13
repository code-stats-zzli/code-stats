# CodeStats

odeStats 是一个完全自研的轻量级 Java Web 平台，底层基于迷你 Tomcat 和 IoC 容器，展示了从零构建企业级框架的核心技术。

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




#### 特技

1.  使用 Readme\_XXX.md 来支持不同的语言，例如 Readme\_en.md, Readme\_zh.md
2.  Gitee 官方博客 [blog.gitee.com](https://blog.gitee.com)
3.  你可以 [https://gitee.com/explore](https://gitee.com/explore) 这个地址来了解 Gitee 上的优秀开源项目
4.  [GVP](https://gitee.com/gvp) 全称是 Gitee 最有价值开源项目，是综合评定出的优秀开源项目
5.  Gitee 官方提供的使用手册 [https://gitee.com/help](https://gitee.com/help)
6.  Gitee 封面人物是一档用来展示 Gitee 会员风采的栏目 [https://gitee.com/gitee-stars/](https://gitee.com/gitee-stars/)
