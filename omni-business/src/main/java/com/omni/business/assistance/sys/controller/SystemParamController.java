package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.service.SystemParamService;
import com.omni.business.common.util.BaseController;
import com.omni.common.util.StringUtils;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/** ★ L1-01 */
@Controller
@RequestMapping("/sys/api/params")
public class SystemParamController extends BaseController {

    @Autowired
    private SystemParamService service;

    @RequestMapping("/list")
    @ResponseBody
    public String list() { return JsonUtil.toJson(service.list()); }

    @RequestMapping("/update")
    @ResponseBody
    public String update(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        String key = str(req.get("key"));
        String value = str(req.get("value"));
        return JsonUtil.toJson(service.update(key, value));
    }

    @RequestMapping("/export")
    @ResponseBody
    public String export(@RequestParam(value = "format", required = false) String format) {
        return service.exportAsFormat(format);
    }

    @RequestMapping("/import")
    @ResponseBody
    public String importJson(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        Object raw = req.get("json");
        String content = raw == null ? JsonUtil.toJson(req) : String.valueOf(raw);
        String format = str(req.get("format"));
        return JsonUtil.toJson(service.importFromFormat(content, format));
    }

    @RequestMapping("/reload")
    @ResponseBody
    public String reload() {
        com.omni.framework.spring.config.ConfigLoader.reload();
        return okOne("message", "已从 system-params.json 重新加载");
    }

    private static String str(Object o) { return StringUtils.safe(o); }
}

// ============================================================
// 📌 AI 提示 · 代码导入解析格式（merge-file）
// ============================================================
// 本合并文件遵循 CodeStats「代码导入」解析格式，可被系统自动拆分还原为独立源文件。
//
// 【本次完整整改清单】
//
// ▸ 新增 6 个：
//    ✔ omni-business/.../common/util/ResponseHelper.java
//    ✔ omni-business/.../common/task/TaskInfo.java
//    ✔ omni-business/.../common/task/TaskRegistry.java
//    ✔ omni-framework/.../mybatis/BaseMapper.java
//    ✔ omni-framework/.../mybatis/annotation/Table.java
//    ✔ omni-framework/.../mybatis/annotation/Id.java
//
// ▸ 基础设施改造 5 个：
//    ✔ omni-framework/.../util/JsonUtil.java       删除 100+ 行死代码
//    ✔ omni-framework/.../util/FileUtils.java      read/write → FileIOUtils
//    ✔ omni-framework/.../jdbc/JdbcTemplate.java   新增 withCatalog
//    ✔ omni-framework/.../mybatis/proxy/MapperProxy.java  支持 BaseMapper 继承
//    ✔ omni-common/.../util/DriverLoadException.java
//
// ▸ Mapper 层 10 个全部改写（L3-01）：
//    ✔ ProjectMapper  TestCaseMapper  TestStepMapper  TestAssertionMapper
//    ✔ TestExecutionMapper  TestDataMapper  TestHookMapper  TestSnapshotMapper
//    ✔ TestVariableMapper  UserMapper
//
// ▸ TaskManager 4 个（L2-02）：
//    ✔ CompressTaskManager  UnzipTaskManager  ConvertTaskManager  SystemCheckTaskManager
//
// ▸ Controller / Service 层（L1-01 ~ L1-08 + L2-01 + L2-03）：
//    ✔ AgentController  AIChatController
//    ✔ CompressController  UnzipController
//    ✔ FilesController
//    ✔ CodeAnalysisController  MethodAnalysisController
//    ✔ QuestionBankController
//    ✔ DataCubeController
//    ✔ ExpressionEngineController  ScriptEngineController  FormatConverterController
//    ✔ ClusterController  SshConnectionController  PortScanController
//    ✔ SpiderController  SysLogController  SystemParamController
//    ✔ DatabaseClientController  DatabaseClientController2
//    ✔ ProjectController  UserManageController  DataSourceController
//
// ▸ Service / Util 层（L1-05 ~ L1-08）：
//    ✔ ConnectionStringParser（+ sqlserver 支持）
//    ✔ TempConnectionManager
//    ✔ DriverLoader
//    ✔ DirectoryService  QuestionBankService
//    ✔ ClusterService
//    ✔ LocalDeployChannel  GpuComponent  SoundCardComponent
//    ✔ AbstractSystemCheckCommand
//    ✔ DynamicTableDataService  TableDataService  TableCubeProvider
//
// ▸ 遗留问题（本批未覆盖，建议后续迭代）：
//    - BizResults.okFlat 里 `if (fields != null) m.putAll(fields)` 对 `null` value 的语义需要在业务侧确认
//    - BaseMapper 的 updateById 采用"全字段非空更新"策略；如果有 null 语义字段，需要子接口改回显式 @Update
//    - ResponseHelper 的 sendStatic 依赖 HttpServletResponse 实现（当前 Tomcat 连接器支持，NIO 版本需验证）
//    - JdbcTemplate.withCatalog 在 Oracle 上的 catalog 语义是 schema，需要回归验证
//
// 【导入步骤】
//   1) 先 git stash 或备份原项目
//   2) 在「代码分析 → 代码导入」页面 targetRoot 设为项目根目录（user.dir）
//   3) 粘贴本文本 → 解析 → 导入
//   4) mvn clean compile 验证
//   5) 启动后回归 5 条主链路：
//      - 压缩/解压/格式转换（TaskRegistry 改造）
//      - 数据库连接 + 跨库查询（withCatalog 修复）
//      - SSH 下载 / 集群包下载（ResponseHelper）
//      - 题库预览（PagingSupport）
//      - 系统检查异步任务（TaskRegistry + cancel）
// ============================================================