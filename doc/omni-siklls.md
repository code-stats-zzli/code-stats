CodeStats 平台完整技能文档
1. 项目概述
CodeStats 是一个全栈式开发辅助平台，完全自研了轻量级 Java Web 容器（Tomcat 风格）和 IoC/MVC 框架，并在此基础上构建了多个实用工具模块：

数据库元数据浏览器

多线程代码统计分析器（支持 Web 界面 / GUI / 命令行）

文件目录管理器（本地 + 远程根路径）

AI Agent 对话（对接 Ollama / DeepSeek API / 自定义 API）

项目介绍与导航（动态内容展示）

同时提供两个前端版本：

demo/ —— 原生 JavaScript + iframe，稳定可靠

face/ —— 自研 SimpleVue 框架（运行时模板解析 + 响应式）的实践演示

项目整体展示了从底层容器到上层业务的全栈开发能力，代码量适中，适合学习自研框架、代码分析算法、元数据操作等。

2. 核心功能模块
2.1 数据库浏览器 (/dbclient)
支持 MySQL 连接（可配置多数据源）

左侧树形导航：连接 → 数据库 → 表 → 列/视图/索引

右侧 Tab：属性（显示表引擎、字符集、列详情）、数据（预留）、ER 图（预留）

API: /dbclient/api/databases, /dbclient/api/tables, /dbclient/api/columns

2.2 代码分析器 (/code)
扫描指定根目录 + 额外路径，按扩展名过滤，支持排除路径和 .gitignore

多线程并行分析每个文件：识别语言、统计总行数、代码行、注释行、空行、简单复杂度（关键字计数）

结果可按行数/文件名/语言/复杂度排序，输出 JSON/CSV/Console 格式

可选将收集到的所有文件内容合并到一个文本文件（带文件头注释）

Web 界面（demo/code/codeStats.html）支持配置、展示表格、导出 CSV、下载合并文件

GUI 版本（com.omni.framework.codestats.gui.CodeStatsGUI）Swing 桌面程序

命令行版本：java -jar codestats.jar -d <dir> -e java -o json

2.3 目录管理器 (/files)
支持两种根类型：本地目录（配置 files.local.root）和服务器目录（配置 files.server.root，例如 D:/temp）

树形目录浏览（懒加载）、文件列表展示（名称/类型/大小/修改时间）

文件操作：新建文件/目录、重命名、删除、上传、下载（文件直接下载，目录打包为 ZIP）

内置编辑：读取/保存文本文件

安全性：路径越界检查（resolveSafePath 确保操作在根目录内）

2.4 AI Agent (/agent)
后端支持三种模式：

Ollama（本地）：调用 http://localhost:11434/api/chat，需提前安装 Ollama 并拉取模型

DeepSeek 官网 API：需要配置 deepseek.api.key

自定义 API：支持 OpenAI 兼容格式

前端聊天界面：选择模型、温度、后端类型，发送消息并保持对话历史

部署辅助：检查 Ollama 状态、拉取模型、运行模型（验证已安装）

2.5 项目介绍与导航 (/api/project)
提供 /api/project/nav 返回模块列表，/api/project/detail?moduleId=... 返回各模块详细 HTML 描述

内容涵盖：项目架构、自研框架原理、数据库模块、代码分析、目录管理、Agent

2.6 自研 Web 框架 & 容器
嵌入式 Tomcat：Connector (BIO 线程池)、Pipeline-Valve 责任链、Context/Host/Engine/Server 容器层次、WebappClassLoader 隔离

IoC 容器：SimpleApplicationContext 扫描包（支持通配符 *、**），实例化 @Controller / @Service / @Repository / @Component / @Mapper，自动 @Autowired 注入

MVC 核心：DispatcherServlet 映射 @RequestMapping，@ResponseBody 自动 JSON 序列化（JsonUtil 极简实现），@RequestParam 参数绑定

MyBatis 风格 Mapper：@Mapper 接口 + @Select 注解，动态代理执行 SQL（基于 JdbcTemplate）

JDBC 连接池：SimpleDataSource 阻塞队列 + Semaphore 限流，支持空闲连接回收、验证查询

日志框架：自研 LoggerFactory，支持 XML 配置、Appender（Console/File/RollingFile/Async）、PatternLayout、过滤器

3. 技术栈与依赖
后端（Java）
JDK 8+

无第三方依赖（除非显式使用 Caffeine、Playwright，但这些是可选的）

内嵌自研 Web 容器，直接启动 Bootstrap.main()

前端（demo/）
原生 HTML/CSS/JS，科技感玻璃拟态风格（global-tech.css）

global.js 提供上下文路径工具 apiUrl()

各模块独立 HTML + JS，通过 iframe 集成到主页面 demo/index.html

前端（face/ – SimpleVue 演示）
自研 SimpleVue 框架（约 500 行）：

响应式：observe() 基于 Object.defineProperty，Dep 收集依赖，Watcher 触发更新

模板编译：解析 {{ }} 插值、v-model、v-if、v-show、@click、:class（支持静态类合并）

组件化：Component 类，定义 template、data()、methods、style（scoped CSS）

用于演示如何用纯 JS 实现类 Vue 框架

4. 快速启动与配置
4.1 环境要求
Java 8+

MySQL（可选，用于数据库模块；无数据库时其他模块仍可运行）

Ollama（可选，用于 Agent 本地模型）

4.2 配置文件
在类路径（src/main/resources）下创建 application.properties：

properties
# 服务器端口
server.port=28080
# 上下文路径（访问前缀）
context.path=/omni
# 扫描的包（Controller/Service/Mapper）
scan.packages=com.omni.business

# 数据源（数据库模块需要）
db.url=jdbc:mysql://localhost:3306/yourdb?useSSL=false
db.username=root
db.password=123456
db.maxActive=10

# Ollama（Agent 本地模式）
ollama.api.url=http://localhost:11434/api/chat

# DeepSeek API Key（可选）
deepseek.api.key=sk-xxxxx

# 目录管理根路径
files.local.root=D:/work/space/CodeStats
files.server.root=D:/temp

# Playwright 启用（用于自定义后端模拟浏览器）
playwright.enabled=false
4.3 启动服务
运行 com.omni.Bootstrap 的 main 方法。启动后访问：

主界面：http://localhost:28080/omni/demo/index.html

SimpleVue 演示：http://localhost:28080/omni/face/index.html

4.4 代码分析独立运行（命令行）
bash
java -cp codestats.jar com.omni.run.Application -d ./src -e java -o console
支持的参数：

-d 根目录

-e 扩展名（逗号分隔）

-x 排除路径

-c 是否排除注释（true/false）

-t 线程数

-s 排序（lines/name/language/complexity）

-o 输出格式（console/json/csv）

--extra-paths 额外文件/目录（逗号分隔）

--gui 启动 Swing GUI

5. API 接口列表
所有接口前缀依赖于 context.path（默认 /omni），以下列出相对路径。

5.1 数据库浏览器
路径	方法	说明
/dbclient/api/databases	GET	返回所有数据库名数组
/dbclient/api/tables?db=xxx	GET	返回指定数据库的表列表（name, type）
/dbclient/api/columns?db=xxx&table=yyy	GET	返回表的列详细信息
5.2 代码分析
路径	方法	说明
/code/api/config	GET	获取默认配置（JSON）
/code/api/analyze	GET	执行分析，参数见 Web 界面
/code/api/download?filePath=<base64>	GET	下载合并文件（需 Base64 编码路径）
5.3 目录管理
路径	方法	说明
/files/api/root?rootType=local	GET	获取根信息
/files/api/tree?rootType=local&path=&withChildren=true	GET	树节点
/files/api/list?rootType=local&path=	GET	目录列表
/files/api/read?rootType=local&path=file.txt	GET	读取文本文件
/files/api/save	POST	保存文件（JSON body）
/files/api/mkdir	POST	创建目录
/files/api/rename	POST	重命名
/files/api/delete	POST	删除
/files/api/upload	POST	上传文件（Base64）
/files/api/download?rootType=local&path=file.txt	GET	下载文件
/files/api/downloadZip?rootType=local&path=dir	GET	下载目录为 ZIP
5.4 AI Agent
路径	方法	说明
/agent/api/ollama/status	GET	检查 Ollama 是否可用
/agent/api/ollama/pull	POST	拉取模型（同步，可能超时）
/agent/api/ollama/run	POST	验证模型已安装
/agent/api/chat	POST	对话接口（支持 Ollama/DeepSeek/Custom）
/deepseek/api/chat（开源工具专用）	POST	模拟浏览器版 DeepSeek（需 Playwright）
5.5 项目介绍
路径	方法	说明
/api/project/nav	GET	导航菜单（id, title, summary）
/api/project/detail?moduleId=arch	GET	模块详细 HTML
6. 前端使用说明
6.1 主平台 (demo/index.html)
顶部 Tab 切换不同模块，各模块使用 iframe 嵌入独立页面

每个子模块有自己的 UI 和交互，通过 window.apiUrl() 调用后端接口（自动处理上下文路径）

6.2 代码分析 Web 界面
填写根目录、扩展名（逗号分隔）、排除路径等

可选“启用文件合并”并指定输出文件路径

点击“开始分析”后显示进度条，完成后展示文件列表表格和摘要统计

可导出表格为 CSV，下载合并后的文件

6.3 数据库浏览器
左侧树默认显示 localhost 和 localhost:3307 两个连接（可修改前端源码或后端动态读取）

展开连接 → 加载数据库列表 → 点击数据库 → 加载表列表 → 点击表 → 右侧显示列属性

6.4 SimpleVue 演示 (face/index.html)
展示了用自研框架构建的五个组件：项目介绍、数据库、目录管理、Agent、代码分析

切换 Tab 使用 :class 动态绑定（已修复静态类合并问题）

组件内部使用 v-model、@click、v-if 等指令，数据响应式更新

7. 自研框架原理详解
7.1 Web 容器（Tomcat 模拟）
Lifecycle 接口：统一生命周期（init/start/stop/destroy）

Pipeline-Valve：每个容器（Engine/Host/Context/Wrapper）都有一个 Pipeline，Valve 责任链依次处理请求

请求流程：Connector 接受 Socket → 解析为 Request/Response → 调用 Engine 的 Pipeline → 根据 Host → 根据 Context（匹配 context.path）→ 根据 Wrapper（Servlet）→ 执行 service 方法

7.2 IoC 容器
扫描指定包（支持 * 和 ** 通配符）下所有类

筛选出带有 @Controller/@Service/@Repository/@Component/@Mapper 注解的类

实例化（调用无参构造），存入 Map<Class, Object>

遍历所有 Bean，对标注 @Autowired 的字段进行依赖注入（根据类型匹配）

对于 @Mapper 接口，使用 Proxy.newProxyInstance 创建代理，执行 @Select 中的 SQL（通过 JdbcTemplate）

7.3 MVC 处理
DispatcherServlet 初始化时收集所有 @Controller 中 @RequestMapping 方法，建立 URL → (controller, method, isResponseBody) 映射

请求到达后，根据 URI 查找 Handler，使用 ParamBinder 解析参数（支持 @RequestParam、自动注入 Request/Response）

若方法有 @ResponseBody，将返回值通过 JsonUtil 序列化（或直接字符串）输出；否则调用 ViewResolver 渲染（简单实现）

7.4 代码分析引擎（Pipeline）
五个阶段：收集（过滤文件）→ 并行分析（多线程统计）→ 聚合（排序）→ 输出（格式化）→ 可选 合并文件

LanguageDetector 根据扩展名或 shebang 识别语言，LanguageSpec 定义注释规则

分析时逐行判断：块注释状态、单行注释、空行，并累加复杂度关键字

7.5 响应式框架（SimpleVue）
响应式：observe() 递归遍历对象属性，用 Object.defineProperty 转换为 getter/setter，每个属性关联一个 Dep（订阅者列表）

Watcher：在表达式求值时将自身设置为 Dep.target，触发 getter 时收集依赖；数据变化时调用所有 Watcher 的 update() 重新执行相关更新函数

模板编译：递归遍历 DOM 节点，处理指令：

v-model：绑定 value 和 input 事件，更新 $data

v-if/v-show：根据表达式动态添加/移除节点或切换 display

@click：注册事件，支持方法调用及参数

:class / v-bind:class：合并静态类与动态类表达式

注：框架为教育演示，未实现虚拟 DOM、列表渲染等高级特性

8. 常见问题与解决方案
Q1: 启动后访问页面空白？
检查控制台是否有错误，尤其是 JavaScript 报 apiUrl 未定义 → 确保 global.js 已加载

检查后端是否正常启动（Catalina 日志显示端口监听）

对于 face 版本，确认 Compiler.js 已支持 : 简写（已修复）

Q2: 数据库模块无法显示表？
检查 application.properties 中数据库连接是否正确

确认 MySQL 驱动已包含（项目中默认使用 mysql-connector-java 8.0+）

后端日志是否抛出 SQL 异常

Q3: 代码分析找不到文件？
确保填写的根目录存在且包含符合扩展名的文件

检查排除路径是否误杀了目标路径

如使用额外路径，注意路径分隔符使用 / 或 \\

Q4: Agent 对话无响应？
若使用 Ollama：先运行 ollama serve 确保服务启动，并在终端执行 ollama list 查看模型是否已拉取

若使用 DeepSeek API：检查 deepseek.api.key 是否配置正确，且账户有余额

若使用自定义 API：确保 URL 支持 OpenAI 风格的 /v1/chat/completions，并正确设置 API Key

Q5: :class 动态绑定不生效（SimpleVue 版本）？
已修复：bindProp 方法中保存静态类，每次更新时合并静态类与动态类表达式

确保 CSS 定义 .tab-panel 默认 display: none，.tab-panel.active 为 display: block

9. 扩展开发指南
9.1 新增 Controller
创建类，标注 @Controller 和 @RequestMapping("/yourpath")

添加方法，标注 @RequestMapping("/subpath")，可返回 String 或任意对象（配合 @ResponseBody）

方法参数支持 @RequestParam、HttpServletRequest、HttpServletResponse

如需业务逻辑，注入 @Service Bean

9.2 新增 Mapper
定义接口，标注 @Mapper

方法上标注 @Select("SQL 语句")，参数占位符用 ?

返回类型支持 String / Integer / List<String> 等

9.3 扩展代码分析语言
在 Language 枚举中添加新语言

在 LanguageSpec.forLanguage() 中定义注释符号和扩展名

语言检测器 LanguageDetector 会自动根据扩展名识别

9.4 自定义前端组件（SimpleVue）
定义 Component 子类，实现 data() 返回响应式数据对象

实现 initMethods() 注册 methods（key -> Function）

设置 template 字符串（支持插值和指令）

调用 mount(selector) 挂载

10. 总结
CodeStats 是一个功能完整、代码优雅的全栈项目，适合：

学习自研 Web 容器、IoC、MVC、ORM、连接池、日志框架

学习代码分析算法（行数统计、语言识别、注释排除）

学习元数据 API 的应用（数据库浏览器）

实践前后端分离与自研前端框架

项目代码完全开源，无重型依赖，易于理解与二次开发。如有任何问题，欢迎查阅源码或联系作者。