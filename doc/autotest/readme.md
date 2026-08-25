好的，我按照你的要求，先给出完整的表结构设计，再列出前端所有功能与交互，接着是后端引擎所有操作，最后再串联成完整流程。

第一部分：完整数据库表结构设计（9张表）
1. 项目/模块表（project）
   sql
   CREATE TABLE project (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   project_name VARCHAR(100) NOT NULL COMMENT '项目名称',
   project_code VARCHAR(50) UNIQUE NOT NULL COMMENT '项目编码',
   description VARCHAR(500) COMMENT '项目描述',
   status TINYINT DEFAULT 1 COMMENT '1-启用 0-禁用',
   create_by VARCHAR(50),
   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
   );
2. 用例表（test_case）
   sql
   CREATE TABLE test_case (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   project_id BIGINT NOT NULL COMMENT '所属项目ID',
   case_name VARCHAR(200) NOT NULL COMMENT '用例名称',
   case_code VARCHAR(50) UNIQUE COMMENT '用例编号',
   module_path VARCHAR(200) COMMENT '模块路径，如：用户管理/登录模块',
   priority TINYINT DEFAULT 2 COMMENT '优先级: 1-High 2-Medium 3-Low',
   env_type TINYINT DEFAULT 1 COMMENT '环境: 1-测试 2-预发 3-生产',
   browser_type VARCHAR(20) DEFAULT 'chrome' COMMENT 'chrome/firefox/edge',
   base_url VARCHAR(500) COMMENT '基础URL，步骤中的相对路径基于此拼接',
   timeout_sec INT DEFAULT 30 COMMENT '全局超时(秒)',
   retry_times INT DEFAULT 0 COMMENT '失败重试次数',
   status TINYINT DEFAULT 1 COMMENT '1-启用 0-禁用',
   tags VARCHAR(200) COMMENT '标签，逗号分隔: smoke,regression',
   create_by VARCHAR(50),
   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   INDEX idx_project (project_id),
   INDEX idx_status (status)
   );
3. 步骤表（test_step）- 最核心
   sql
   CREATE TABLE test_step (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   case_id BIGINT NOT NULL COMMENT '所属用例ID',
   step_order INT NOT NULL COMMENT '执行顺序，从1开始',
   parent_step_id BIGINT DEFAULT NULL COMMENT '父步骤ID，支持子步骤（如循环体）',
   step_type TINYINT DEFAULT 1 COMMENT '1-普通步骤 2-循环开始 3-循环结束 4-条件判断',

   -- 动作相关
   action_type VARCHAR(30) NOT NULL COMMENT 'goto/click/input/select/upload/hover/doubleClick/rightClick/drag/drop/scrollTo/focus/blur/submit/clear/selectAll/pressKey/wait/sleep/assert/assertVisible/assertText/assertAttribute/assertUrl/assertTitle/assertExist/assertNotExist/extract/setVariable/if/else/endIf/for/endFor/foreach/endForeach/switchFrame/switchWindow/switchAlert/acceptAlert/dismissAlert/sendAlert/executeScript/reload/back/forward/closeWindow/screenshot/log',

   -- 定位相关
   locator_type VARCHAR(20) COMMENT 'id/name/className/tagName/linkText/partialLinkText/xpath/cssSelector/role/text/label/placeholder/alt/title/position',
   locator_value VARCHAR(1000) COMMENT '定位表达式',
   locator_priority TEXT COMMENT '多重定位策略，JSON数组，格式: [{"type":"id","value":"btn"},{"type":"xpath","value":"//button"}]',

   -- 操作值
   action_value VARCHAR(4000) COMMENT '输入值/URL/JS脚本/快捷键组合',
   action_value_type TINYINT DEFAULT 1 COMMENT '1-普通文本 2-变量引用({{xxx}}) 3-JSON对象 4-Base64文件',

   -- 等待配置
   wait_type VARCHAR(20) DEFAULT 'none' COMMENT 'none/implicit/explicit/untilVisible/untilClickable/untilPresent/untilText/untilValue/untilTitle/untilUrl',
   wait_seconds INT DEFAULT 0 COMMENT '等待秒数',
   wait_until_value VARCHAR(500) COMMENT '等待的预期值（如untilText时用）',

   -- 失败处理
   fail_handle TINYINT DEFAULT 1 COMMENT '1-停止用例 2-继续下一步 3-重试当前步 4-截图并继续',
   ignore_exception TINYINT DEFAULT 0 COMMENT '1-忽略异常继续执行（用于清理步骤）',

   -- 日志与备注
   step_desc VARCHAR(200) COMMENT '步骤描述，用于日志展示',

   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   INDEX idx_case_order (case_id, step_order),
   INDEX idx_parent (parent_step_id),
   FOREIGN KEY (case_id) REFERENCES test_case(id) ON DELETE CASCADE
   );
4. 断言表（test_assertion）
   sql
   CREATE TABLE test_assertion (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   case_id BIGINT NOT NULL COMMENT '所属用例ID',
   step_id BIGINT COMMENT '可关联具体步骤，也可独立存在',
   assert_order INT NOT NULL COMMENT '断言执行顺序',

   assert_type VARCHAR(30) NOT NULL COMMENT 'text/attribute/url/title/exist/notExist/visible/notVisible/enabled/disabled/selected/checked/value/count/css/property/compare/cookie/response/contains/notContains/matches/notMatches/greaterThan/lessThan/equal/notEqual',

   -- 定位（部分断言不需要）
   locator_type VARCHAR(20) COMMENT 'id/name/xpath/css等',
   locator_value VARCHAR(1000) COMMENT '定位表达式',

   -- 预期值
   expected_value VARCHAR(4000) COMMENT '预期值，支持变量{{}}',
   expected_value_type TINYINT DEFAULT 1 COMMENT '1-普通文本 2-正则表达式 3-变量引用 4-数字比较',

   -- 比较方式
   compare_type VARCHAR(20) DEFAULT 'equals' COMMENT 'equals/contains/startsWith/endsWith/regex/ignoreCase/ignoreSpace/jsonPath/xpath',

   -- 自定义断言脚本（高级）
   custom_script VARCHAR(4000) COMMENT 'JS脚本，返回boolean',

   fail_handle TINYINT DEFAULT 1 COMMENT '1-停止用例 2-继续',
   assert_desc VARCHAR(200) COMMENT '断言描述',

   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   INDEX idx_case (case_id),
   INDEX idx_step (step_id),
   FOREIGN KEY (case_id) REFERENCES test_case(id) ON DELETE CASCADE
   );
5. 变量定义表（test_variable）
   sql
   CREATE TABLE test_variable (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   case_id BIGINT NOT NULL COMMENT '所属用例ID',
   var_name VARCHAR(50) NOT NULL COMMENT '变量名，如 token',
   var_scope VARCHAR(20) DEFAULT 'case' COMMENT 'case/suite/global',

   -- 提取来源
   extract_type VARCHAR(30) NOT NULL COMMENT 'text/attribute/html/value/url/title/cookie/localStorage/sessionStorage/jsonPath/response/header/js',
   locator_type VARCHAR(20) COMMENT '需要定位时使用',
   locator_value VARCHAR(1000) COMMENT '定位表达式',
   extract_expression VARCHAR(500) COMMENT '提取表达式: attribute名/jsonPath/JS表达式',

   -- 默认值
   default_value VARCHAR(1000) COMMENT '提取失败时的默认值',
   is_required TINYINT DEFAULT 0 COMMENT '1-必须存在，否则用例失败',

   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   INDEX idx_case (case_id),
   UNIQUE KEY uk_case_var (case_id, var_name),
   FOREIGN KEY (case_id) REFERENCES test_case(id) ON DELETE CASCADE
   );
6. 前置/后置脚本表（test_hook）
   sql
   CREATE TABLE test_hook (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   case_id BIGINT NOT NULL COMMENT '所属用例ID',
   hook_type TINYINT NOT NULL COMMENT '1-前置脚本(每个步骤前) 2-后置脚本(每个步骤后) 3-用例前执行 4-用例后执行',
   hook_order INT DEFAULT 0 COMMENT '执行顺序',

   script_type VARCHAR(20) DEFAULT 'js' COMMENT 'js/java/groovy',
   script_content TEXT NOT NULL COMMENT '脚本内容',
   script_desc VARCHAR(200) COMMENT '脚本描述',

   enabled TINYINT DEFAULT 1,
   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   INDEX idx_case_type (case_id, hook_type),
   FOREIGN KEY (case_id) REFERENCES test_case(id) ON DELETE CASCADE
   );
7. 测试数据集表（test_data）
   sql
   CREATE TABLE test_data (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   case_id BIGINT COMMENT '关联用例，为空则为全局数据',
   data_name VARCHAR(100) NOT NULL COMMENT '数据集名称',
   data_key VARCHAR(100) NOT NULL COMMENT '数据键',
   data_value VARCHAR(4000) NOT NULL COMMENT '数据值',
   data_type TINYINT DEFAULT 1 COMMENT '1-字符串 2-数字 3-JSON 4-数组 5-Boolean',
   env_type TINYINT DEFAULT 1 COMMENT '1-测试 2-预发 3-生产 0-全部环境',
   description VARCHAR(200),
   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
   update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   INDEX idx_case_env (case_id, env_type),
   UNIQUE KEY uk_case_key (case_id, data_key, env_type)
   );
8. 执行记录表（test_execution）
   sql
   CREATE TABLE test_execution (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   case_id BIGINT NOT NULL,
   execution_code VARCHAR(50) UNIQUE COMMENT '执行编号: EXEC-20260125-001',

   trigger_type TINYINT DEFAULT 1 COMMENT '1-手动 2-定时任务 3-CI/CD触发 4-API调用',
   trigger_user VARCHAR(50) COMMENT '触发人',
   trigger_source VARCHAR(100) COMMENT '触发来源: jenkins/gitlab/手动',

   -- 环境信息
   env_type TINYINT DEFAULT 1,
   browser_type VARCHAR(20),
   browser_version VARCHAR(50),
   os_type VARCHAR(50),
   os_version VARCHAR(50),

   -- 执行结果
   start_time DATETIME,
   end_time DATETIME,
   total_steps INT DEFAULT 0,
   pass_count INT DEFAULT 0,
   fail_count INT DEFAULT 0,
   skip_count INT DEFAULT 0,
   pass_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '通过率: 百分比',

   status TINYINT DEFAULT 0 COMMENT '0-执行中 1-全部通过 2-部分失败 3-全部失败 4-超时 5-取消 6-异常',
   error_message TEXT COMMENT '全局错误信息',

   -- 性能数据
   total_time_ms INT COMMENT '总耗时(毫秒)',
   avg_step_time_ms INT COMMENT '平均步骤耗时',
   max_step_time_ms INT COMMENT '最大步骤耗时',

   -- 报告相关
   report_url VARCHAR(500) COMMENT 'HTML报告地址',
   report_json_url VARCHAR(500) COMMENT 'JSON报告地址',
   video_url VARCHAR(500) COMMENT '录像视频地址（如果有）',
   log_url VARCHAR(500) COMMENT '日志文件地址',

   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

   INDEX idx_case_time (case_id, create_time),
   INDEX idx_status (status),
   INDEX idx_code (execution_code),
   FOREIGN KEY (case_id) REFERENCES test_case(id) ON DELETE CASCADE
   );
9. 步骤快照表（test_snapshot）
   sql
   CREATE TABLE test_snapshot (
   id BIGINT PRIMARY KEY AUTO_INCREMENT,
   execution_id BIGINT NOT NULL COMMENT '关联执行记录',
   step_id BIGINT COMMENT '关联步骤配置ID（如果是断言则关联断言ID）',
   step_order INT NOT NULL COMMENT '执行顺序',
   action_type VARCHAR(30) NOT NULL COMMENT '操作类型',
   step_desc VARCHAR(200) COMMENT '步骤描述',

   -- 快照数据
   html_content MEDIUMTEXT COMMENT 'DOM快照（GZIP压缩后存储）',
   html_size INT COMMENT '压缩前大小(KB)',
   screenshot_url VARCHAR(500) COMMENT '截图OSS地址',
   screenshot_thumbnail_url VARCHAR(500) COMMENT '缩略图地址',

   -- 操作元素信息
   element_tag VARCHAR(50) COMMENT '元素标签名',
   element_selector VARCHAR(500) COMMENT '元素的唯一选择器',
   element_rect VARCHAR(200) COMMENT '元素坐标: {"x":100,"y":200,"w":80,"h":30}',
   element_text VARCHAR(500) COMMENT '操作时的元素文本',

   -- 执行结果
   is_success TINYINT DEFAULT 1 COMMENT '0-失败 1-成功',
   error_msg VARCHAR(2000) COMMENT '错误信息',
   error_stack TEXT COMMENT '异常堆栈',
   exec_time_ms INT COMMENT '该步骤耗时(毫秒)',

   -- 上下文数据
   variables_snapshot TEXT COMMENT '执行到此步时的变量池快照（JSON）',
   cookies_snapshot TEXT COMMENT '当前Cookie快照（JSON）',

   create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

   INDEX idx_exec_order (execution_id, step_order),
   INDEX idx_exec_time (execution_id, create_time),
   FOREIGN KEY (execution_id) REFERENCES test_execution(id) ON DELETE CASCADE
   );
   第二部分：前端所有功能与交互
   一、功能模块全景图
   text
   ┌─────────────────────────────────────────────────────────────────┐
   │                    自动化测试平台（前端）                        │
   ├─────────────────────────────────────────────────────────────────┤
   │ 1. 项目管理  │ 2. 用例管理  │ 3. 执行中心  │ 4. 报告中心     │
   │  ├ 创建/编辑 │  ├ 用例列表  │  ├ 单次执行  │  ├ 执行历史     │
   │  ├ 删除/禁用 │  ├ 用例编辑  │  ├ 批量执行  │  ├ 报告详情     │
   │  └ 成员管理  │  ├ 步骤编排  │  ├ 定时任务  │  ├ 播放回放     │
   │              │  ├ 断言配置  │  └ 执行监控  │  └ 统计分析     │
   │              │  ├ 变量配置  │              │                 │
   │              │  └ 调试运行  │              │                 │
   └─────────────────────────────────────────────────────────────────┘
   二、详细功能点与交互设计
   模块1：项目管理
   功能点	交互方式	说明
   创建项目	弹窗表单	填写项目名称、编码、描述
   项目列表	表格展示	支持搜索、分页、排序
   项目设置	抽屉面板	配置默认浏览器、超时时间等
   模块2：用例管理（核心）
   2.1 用例列表
   text
   布局：左树（项目/模块树）+ 右表（用例列表）
   功能：
- 按项目/模块筛选
- 搜索用例名称
- 批量启用/禁用/删除
- 新建用例按钮
- 复制用例
- 导出/导入用例（JSON/Excel）
  2.2 用例编辑页面（最重要的界面）
  text
  布局：
  ┌────────────────────────────────────────────────────────────┐
  │ 顶部：用例名称输入框  |  保存  |  调试运行  |  更多操作  │
  ├────────────────────────────────────────────────────────────┤
  │  Tab1: 基础配置  |  Tab2: 步骤编排  |  Tab3: 断言管理   │
  │                    |  Tab4: 变量管理  |  Tab5: 前后置脚本 │
  ├────────────────────────────────────────────────────────────┤
  │                                                          │
  │              （根据Tab切换显示不同内容）                    │
  │                                                          │
  └────────────────────────────────────────────────────────────┘
  Tab1: 基础配置

字段	控件	说明
用例名称	输入框	必填
用例编号	输入框	自动生成，可修改
所属项目	下拉框	从已有项目选择
模块路径	级联选择器	支持自定义输入
优先级	下拉框	High/Medium/Low
执行环境	多选下拉	测试/预发/生产
浏览器	下拉框	Chrome/Firefox/Edge
基础URL	输入框	如 https://example.com
超时时间	数字输入框	单位秒
标签	输入框（标签组件）	支持多标签
用例描述	文本域	说明性文字
Tab2: 步骤编排（最核心界面）

text
展示：表格形式，每行一个步骤
┌────┬────────┬──────────┬──────────────┬──────────┬──────────┬──────────┬──────┐
│ 序号│ 操作类型│ 定位方式  │ 定位表达式    │ 操作值   │ 等待(秒) │ 失败处理 │ 操作 │
├────┼────────┼──────────┼──────────────┼──────────┼──────────┼──────────┼──────┤
│  1 │ goto   │ url      │ /login       │          │    0     │  停止    │ ...  │
│  2 │ input  │ id       │ username     │ admin    │    0.5   │  停止    │ ...  │
│  3 │ click  │ xpath    │ //button     │          │    1     │  截图    │ ...  │
│  4 │ assert │ text     │ #welcome     │ 欢迎回来  │    0     │  停止    │ ...  │
├────┴────────┴──────────┴──────────────┴──────────┴──────────┴──────────┴──────┤
│  [+ 添加步骤]  [批量导入]  [清除所有]  [展开高级设置]                         │
└────────────────────────────────────────────────────────────────────────────────┘
交互细节：

添加步骤：点击后出现步骤配置表单（抽屉或行内编辑），选择操作类型后动态显示对应配置项。

动态表单：选择input显示"输入值"；选择upload显示"文件路径"；选择select显示"选项文本/值"。

拖拽排序：通过拖拽行首的图标调整顺序。

复制/禁用/删除：每行右侧操作按钮。

代码模式切换：右上角开关，切换到JSON编辑模式（适合高级用户批量编辑）。

步骤模板：提供常用步骤模板（如"登录"、"搜索"），一键插入。

Tab3: 断言管理

text
表格展示所有断言点（可独立配置，也可与步骤关联）
┌────┬──────────┬──────────┬──────────────┬──────────────┬────────────┬──────┐
│ 序号│ 断言类型 │ 定位方式  │ 定位表达式    │ 预期值      │ 比较方式   │ 操作 │
├────┼──────────┼──────────┼──────────────┼──────────────┼────────────┼──────┤
│  1 │ text     │ id       │ welcome-msg  │ 登录成功    │ contains   │ ...  │
│  2 │ url      │ -        │ -            │ /dashboard   │ contains   │ ...  │
│  3 │ exist    │ xpath    │ //div[@role] │ true        │ equals     │ ...  │
└────┴──────────┴──────────┴──────────────┴──────────────┴────────────┴──────┘
Tab4: 变量管理

text
表格定义变量提取规则
┌────┬──────────┬──────────────┬──────────────┬──────────────┬──────────┬──────┐
│ 序号│ 变量名   │ 提取来源      │ 定位方式     │ 提取表达式   │ 默认值   │ 操作 │
├────┼──────────┼──────────────┼──────────────┼──────────────┼──────────┼──────┤
│  1 │ token    │ attribute    │ id           │ data-token   │          │ ...  │
│  2 │ orderId  │ text         │ #order-id    │ -            │ 0        │ ...  │
└────┴──────────┴──────────────┴──────────────┴──────────────┴──────────┴──────┘
Tab5: 前后置脚本

text
两个区域：
┌────────────────────────────────────────────────────────────┐
│ 前置脚本（步骤执行前）                                    │
│ [代码编辑器 - Monaco Editor]                              │
│ // 示例：清除本地缓存                                    │
│ window.localStorage.clear();                             │
├────────────────────────────────────────────────────────────┤
│ 后置脚本（步骤执行后）                                    │
│ [代码编辑器 - Monaco Editor]                              │
│ // 示例：记录执行日志                                    │
│ console.log('Step executed:', stepName);                 │
└────────────────────────────────────────────────────────────┘
2.3 用例调试
调试按钮：在用例编辑页点击"调试运行"

调试面板：页面右侧或底部弹出，显示实时执行日志

单步调试：支持"下一步"、"暂停"、"停止"

断点设置：在步骤行点击设置断点，执行到该步暂停

变量查看：调试面板显示当前变量池内容

模块3：执行中心
功能	交互
单次执行	选择用例 → 点击"执行" → 实时日志输出
批量执行	勾选用例 → 点击"批量执行" → 选择执行策略（串行/并行）
定时任务	创建定时任务 → 选择用例 → 设置Cron表达式 → 通知配置
执行监控	实时显示进度条、当前步骤、耗时
模块4：报告中心
功能	交互
执行历史列表	表格展示，按时间倒序，显示通过率、耗时
报告详情页	概览（通过率图表）+ 步骤详情列表
播放回放	时间轴+画面（iframe渲染快照），支持播放/暂停/快进
统计分析	ECharts图表展示：趋势图、模块通过率、耗时分布
第三部分：后端引擎所有操作（动作字典）
后端引擎将所有操作抽象为动作处理器（ActionHandler），每个操作类型对应一个处理器实现。以下是完整的动作类型列表（共60+种）：

一、导航与控制类（10种）
动作类型	功能	参数说明
goto	打开URL	value: URL地址
back	浏览器后退	-
forward	浏览器前进	-
reload	刷新页面	-
closeWindow	关闭当前窗口	-
closeAllWindows	关闭所有窗口	-
switchWindow	切换窗口	value: 窗口标题/句柄/索引
switchFrame	切换iframe	locator: iframe定位
switchDefaultContent	切回主文档	-
switchParentFrame	切回父级iframe	-
二、元素定位类（定位方式，13种）
定位方式	说明
id	ID属性
name	Name属性
className	Class属性
tagName	标签名
linkText	链接文本（精确）
partialLinkText	链接文本（模糊）
xpath	XPath表达式
cssSelector	CSS选择器
role	ARIA角色（Playwright）
text	文本内容
label	关联label文本
placeholder	placeholder属性
alt/title	alt或title属性
三、元素交互类（15种）
动作类型	功能	参数说明
click	单击	支持偏移量offsetX/offsetY
doubleClick	双击	-
rightClick	右键点击	-
input	输入文本	value: 输入内容
clear	清空输入框	-
submit	提交表单	-
select	下拉选择	value: 选项文本/值/索引
selectByIndex	按索引选择	value: 索引号
selectByValue	按值选择	value: 选项值
selectByText	按文本选择	value: 选项文本
upload	文件上传	value: 文件路径
hover	鼠标悬停	-
focus	聚焦元素	-
blur	失焦元素	-
scrollTo	滚动到元素	-
四、高级交互类（5种）
动作类型	功能	参数说明
drag	拖拽元素	value: 目标定位JSON
dragAndDrop	拖拽并释放	value: 目标定位JSON
dragByOffset	按偏移量拖拽	value: {"x":100,"y":50}
pressKey	按下键盘键	value: 键名，如Enter/Tab
pressKeyCombination	组合键	value: ctrl+a/shift+tab
五、等待类（8种）
动作类型	功能	参数说明
wait	显式等待	waitSeconds: 秒数
sleep	强制休眠	waitSeconds: 秒数
waitUntilVisible	等待元素可见	locator+waitSeconds
waitUntilClickable	等待元素可点击	locator+waitSeconds
waitUntilPresent	等待元素存在	locator+waitSeconds
waitUntilText	等待文本出现	locator+value(文本)+waitSeconds
waitUntilUrl	等待URL匹配	value(URL正则)+waitSeconds
waitUntilTitle	等待标题匹配	value(标题)+waitSeconds
六、断言类（20种）
动作类型	功能	说明
assert	通用断言	配合assertType使用
assertText	断言文本	比较元素文本
assertValue	断言值	比较input的value
assertAttribute	断言属性	比较元素属性
assertUrl	断言URL	比较当前URL
assertTitle	断言标题	比较页面标题
assertExist	断言存在	元素存在于DOM
assertNotExist	断言不存在	元素不存在
assertVisible	断言可见	元素可见
assertNotVisible	断言不可见	-
assertEnabled	断言可用	-
assertDisabled	断言禁用	-
assertChecked	断言选中	checkbox/radio
assertCount	断言数量	匹配元素数量比较
assertContains	断言包含	文本包含子串
assertNotContains	断言不包含	-
assertMatches	断言匹配	正则匹配
assertGreaterThan	断言大于	数值比较
assertLessThan	断言小于	-
assertEqual	断言相等	两个变量比较
七、变量与数据处理类（6种）
动作类型	功能	参数说明
extract	提取变量	value: 变量名，extractType指定来源
setVariable	设置变量	value: {"key":"var1","value":"xxx"}
extractJsonPath	JSON提取	从响应/元素中提取JSONPath
extractRegex	正则提取	从文本中用正则提取
saveCookie	保存Cookie	将当前Cookie存入变量
saveScreenshot	保存截图	将当前截图存入变量
八、逻辑控制类（6种）
动作类型	功能	参数说明
if	条件判断	value: 条件表达式
else	否则分支	-
endif	结束判断	-
for	循环开始	value: {"var":"i","start":1,"end":10}
endFor	循环结束	-
foreach	遍历循环	value: {"list":"{{items}}","var":"item"}
九、弹窗处理类（5种）
动作类型	功能	参数说明
switchAlert	切换到弹窗	-
acceptAlert	确认弹窗	-
dismissAlert	取消弹窗	-
sendAlert	输入到弹窗	value: 输入文本
assertAlertText	断言弹窗文本	value: 预期文本
十、脚本与扩展类（4种）
动作类型	功能	参数说明
executeScript	执行JS	value: JS脚本
executeAsyncScript	异步JS	value: JS脚本（含回调）
log	打印日志	value: 日志内容
screenshot	截图保存	value: 文件名
第四部分：端到端完整流程
流程一：配置用例流程
流程二：执行用例流程
流程三：报告查看与播放流程
完整数据流示例：一个登录用例
用例配置数据：

json
{
"caseName": "登录功能测试",
"baseUrl": "https://example.com",
"steps": [
{"order":1, "action":"goto", "value":"/login"},
{"order":2, "action":"input", "locatorType":"id", "locatorValue":"username", "value":"admin"},
{"order":3, "action":"input", "locatorType":"id", "locatorValue":"password", "value":"123456"},
{"order":4, "action":"click", "locatorType":"xpath", "locatorValue":"//button[text()='登录']"},
{"order":5, "action":"waitUntilUrl", "value":"/dashboard", "waitSeconds":5}
],
"assertions": [
{"type":"text", "locatorType":"id", "locatorValue":"welcome", "expected":"欢迎回来", "compareType":"contains"}
]
}
执行过程数据流：

前端提交 → 后端存库 → 返回case_id

用户点击执行 → 引擎加载配置

步骤1：goto → 浏览器打开 https://example.com/login

步骤2：input → 定位 #username → 输入 admin

步骤3：input → 定位 #password → 输入 123456

步骤4：click → 定位 //button[text()='登录'] → 点击

步骤5：waitUntilUrl → 等待URL包含 /dashboard，超时5秒

断言：获取 #welcome 文本 → 检查是否包含"欢迎回来"

每步抓取HTML快照 → 存入test_snapshot

执行完成 → 更新test_execution状态为"通过" → 返回报告

以上就是完整的表结构设计、前端功能交互、后端引擎操作和端到端流程。这套设计足以支撑一个企业级自动化测试平台的开发。如果你想开始编码，我可以先提供后端ActionHandler的接口设计和核心实现代码，需要的话告诉我