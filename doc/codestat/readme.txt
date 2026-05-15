好的，我已经仔细阅读并记住了您提供的所有项目代码文件内容。下面我将为您系统地梳理这个 CodeStats 项目的架构设计、功能模块和核心流程。

一、项目架构图（文字 + 流程图）
1. 模块分层架构
text
┌─────────────────────────────────────────────────────────────┐
│                      Application (启动类)                     │
│              CodeStatsEngine.run(Application.class, args)    │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    引擎层 (engine)                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  CodeStatsEngine   │  EngineTemplate (抽象模板)      │    │
│  │  DefaultEngineTemplate (默认实现)                    │    │
│  └─────────────────────────────────────────────────────┘    │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│  配置层       │   │   收集/过滤层  │   │   统计/输出层  │
│  config       │   │   collector   │   │  statistics   │
│               │   │   filter      │   │  output       │
│ Config        │   │ FileCollector │   │ StatisticEngine│
│ ConfigLoader  │   │ CompositeFilter│  │ LineAndComment...│
│ GitIgnore...  │   │ Extension...  │   │ Complexity...  │
│               │   │ ExcludePath...│   │ OutputFormatter│
│               │   │ GitIgnore...  │   │ Console/Json...│
└───────────────┘   └───────────────┘   └───────────────┘
                            │
                    ┌───────┴───────┐
                    ▼               ▼
            ┌─────────────┐  ┌─────────────┐
            │ 语言识别层   │  │  排序层     │
            │ language    │  │  sorter     │
            │ Language    │  │ ResultSorter│
            │ Detector    │  │ ByName...   │
            │ Spec        │  │ ByLines...  │
            └─────────────┘  └─────────────┘
2. 主执行流程（模板方法）
text
[EngineTemplate.execute()]
       │
       ├─ 1. init()                 // 初始化（可扩展）
       │
       ├─ 2. buildFilterChain()     // 构建过滤器链
       │      ├─ ExtensionFilter    // 扩展名白名单
       │      ├─ ExcludePathFilter  // 路径关键字排除
       │      └─ GitIgnoreFilter    // .gitignore 模式
       │
       ├─ 3. collectFiles()         // 递归收集文件
       │      └─ FileCollector.walkFileTree() → ConcurrentLinkedQueue<Path>
       │
       ├─ 4. statistics()           // 并行统计
       │      ├─ 创建 StatisticEngine (ForkJoinPool 并行流)
       │      ├─ 每个文件: LineAndCommentCounter.analyze()
       │      │      ├─ LanguageDetector.detect() → 识别语言
       │      │      ├─ LanguageSpec → 获取注释语法
       │      │      ├─ 状态机逐行分析 (总行/代码/注释/空行)
       │      │      └─ ComplexityEstimator (可选) → 复杂度估算
       │      └─ 聚合结果到 StatisticsResult
       │
       ├─ 5. sortAndOutput()        // 排序 + 输出
       │      ├─ ResultSorter (按文件名/行数/语言)
       │      ├─ OutputFormatter (Console/Json/Csv/File)
       │      └─ 输出到控制台或文件
       │
       └─ 6. destroy()              // 清理资源
二、核心设计模式应用
设计模式	应用位置	作用
模板方法	EngineTemplate.execute()	定义统计流程骨架，子类可覆盖钩子方法
责任链	CompositeFilter	多个过滤器组合判断文件是否纳入统计
策略	StatisticStrategy、ResultSorter、OutputFormatter	可插拔的统计算法、排序规则、输出格式
建造者	Config.Builder	灵活构造配置对象
工厂方法	OutputManager.getFormatter()	根据输出格式创建对应格式化器
单例	LanguageDetector 静态映射	语言检测规则全局共享
服务加载器（SPI预留）	EngineTemplate.loadCustomFilters()	支持自定义过滤器扩展
三、主要功能模块详解
1. 配置模块 (config)
Config：不可变配置对象（根目录、扩展名、排除路径、线程数、排序方式、输出格式等）。

ConfigLoader：支持三种配置源（优先级：命令行 > 环境变量 > YAML文件）。

GitIgnoreLoader：解析 .gitignore 并转换为 PathMatcher。

2. 文件收集与过滤 (collector + filter)
FileCollector：使用 Files.walkFileTree 递归遍历目录。

CompositeFilter：责任链模式，所有过滤器都通过才接受文件。

ExtensionFilter：按扩展名白名单过滤。

ExcludePathFilter：按路径关键字排除。

GitIgnoreFilter：支持标准 .gitignore 通配符模式。

3. 语言识别 (language)
Language：枚举，支持 30+ 种常见语言。

LanguageDetector：优先按扩展名映射，失败则读取 shebang 行。

LanguageSpec：每种语言的注释语法（单行、块注释起止符）。

LanguageRegistry：允许动态注册自定义语言规范。

4. 统计引擎 (statistics + engine.StatisticEngine)
StatisticStrategy：统计策略接口，可扩展（行数、复杂度、圈复杂度等）。

LineAndCommentCounter：精确统计的核心实现。

基于状态机处理块注释嵌套。

区分代码行、注释行、空行。

识别字符串内的注释（简化处理，忽略字符串中的注释标记）。

ComplexityEstimator：粗略估算代码复杂度（统计 if/for/while 等关键字次数）。

StatisticEngine：使用 ConcurrentLinkedQueue 存储文件列表，借助 parallelStream 进行并行处理，并汇总结果。

5. 结果聚合 (core)
FileInfo：单个文件的统计信息（路径、语言、行数、代码行、注释行、空行、复杂度）。

StatisticsResult：汇总结果（总文件数、总行数、各语言分组统计、总耗时）。

LanguageSummary：内部类，按语言统计文件数、行数等。

6. 排序与输出 (sorter + output)
ResultSorter：排序器接口。

ByNameSorter：按文件路径字母序。

ByLinesSorter：按总行数降序。

ByLanguageSorter：按语言名排序。

OutputFormatter：输出接口。

ConsoleFormatter：美观的控制台表格，同时输出文件详情和语言分组。

JsonFormatter：输出 JSON 格式（带缩进）。

CsvFormatter：输出 CSV 文件。

FileFormatter：将控制台内容写入文件。

四、典型执行流程示例
bash
# 命令行示例
java -jar codestats.jar -d /home/user/project -e java,kotlin -s lines -o json -f report.json
内部执行：

配置加载：解析 -d -e -s -o -f 参数，生成 Config 对象。

过滤器链：

只接受扩展名为 .java 或 .kotlin 的文件。

排除路径包含 build/ test/（如果配置了）。

自动加载项目根目录下的 .gitignore 规则。

文件收集：遍历 /home/user/project，得到符合条件的文件列表。

并行统计：每个文件独立分析：

检测语言（例如 .java → JAVA）。

读取文件内容，状态机解析出 totalLine, codeLine, commentLine, blankLine。

可选：统计复杂度（if/for 等关键字个数）。

结果聚合：累加所有文件的行数，并生成按语言分组的数据。

排序：根据 -s lines 按总行数降序排序 FileInfo 列表。

输出：调用 JsonFormatter 将结果序列化为 JSON，写入 report.json。

五、项目质量与亮点总结
优点
高度可扩展：策略模式、责任链模式、SPI预留保证了新功能的无侵入添加。

配置丰富：支持 YAML、环境变量、命令行，满足不同场景。

性能优化：parallelStream 并行统计，在多核 CPU 上表现优异。

准确性：语言检测 + 状态机解析注释，比简单正则更精确。

输出友好：支持多种格式（控制台表格、JSON、CSV），并自动按语言分组。

可改进之处（与顶级工具差距）
注释解析：状态机未处理字符串内包含注释符号的情况（例如 String s = "// not a comment";），顶级工具如 cloc 使用完整词法分析。

语言识别：仅基于扩展名和 shebang，未使用文件内容特征（如 package、import 等）提高准确率。

复杂度度量：当前仅为简单的关键字计数，真正的圈复杂度需要解析 AST。

增量统计：未实现仅统计变更文件的能力（类似 cloc --diff）。

内存占用：将所有文件信息加载到 List<FileInfo>，大项目可能导致内存压力；可改用流式输出。

六、总结
CodeStats 是一个设计优良、可扩展的代码统计工具，清晰地展示了 Java 中多种设计模式的综合运用。它从零实现了一个完整的“代码行数统计”系统，具备并行处理、多种过滤、多格式输出、按语言分组等实用功能。虽然与 cloc、tokei 等工业级工具相比在细节完善度和性能上尚有差距，但作为教学范例或内部工具已完全足够。

如果您需要进一步优化（例如增加更多的语言支持、完善注释解析、实现增量统计），我可以提供详细的改进方案。