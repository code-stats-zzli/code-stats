---

## 文件 8: `docs/codestats-pipeline.md`

```markdown
# 代码分析引擎 Pipeline 设计

## 1. 设计目标

- 可扩展：分析流程由多个 Stage 串联，可自由增删。
- 并行：文件分析阶段利用多线程提高效率。
- 输出灵活：支持 Console、JSON、CSV 以及文件合并。

## 2. Pipeline 结构
CollectStage → ParallelAnalyzeStage → AggregateStage → OutputStage

text

- **CollectStage**: 递归扫描目录，应用过滤器（扩展名、排除路径、.gitignore），返回待分析文件列表。
- **ParallelAnalyzeStage**: 多线程分析每个文件的行数、代码行、注释行、空行、复杂度，生成 `FileInfo` 列表。
- **AggregateStage**: 按指定排序规则聚合所有 `FileInfo`，生成 `StatisticsResult`。
- **OutputStage**: 根据配置格式（console/json/csv）输出结果，可选将文件内容合并输出。

## 3. 核心类说明

| 类 | 职责 |
|----|------|
| `Stage<T,R>` | 通用阶段接口 |
| `Pipeline` | 管理 Stage 链，按顺序执行 |
| `ParallelAnalyzeStage` | 使用 `CompletableFuture` 并行执行 `AnalyzeStage` |
| `AnalyzeStage` | 单个文件分析，依赖 `LanguageDetector` 和 `LanguageSpec` |
| `OutputFormatter` | 抽象类，子类实现不同输出格式 |

## 4. 过滤器链

`CompositeFilter` 组合多个 `FileFilter`：
- `ExtensionFilter`: 按扩展名过滤
- `ExcludePathFilter`: 排除包含指定关键字的路径
- `GitIgnoreFilter`: 读取 `.gitignore` 规则

## 5. 性能优化

- 使用线程池（默认 CPU 核心数）并发分析。
- 进度监听器 `ProgressMonitor` 提供实时反馈。
- 文件合并时使用 `Files.copy` 零拷贝。

## 6. 使用示例

```java
Config config = ConfigLoader.load(args);
Pipeline<Path, Void> pipeline = Pipeline.createDefault(config);
pipeline.execute(config.getRootDir());