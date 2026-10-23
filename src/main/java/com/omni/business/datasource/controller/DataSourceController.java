package com.omni.business.datasource.controller;

import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.business.datasource.service.DriverJarService;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/datasource/api")
public class DataSourceController {

    private static final Logger log = LoggerFactory.getLogger(DataSourceController.class);

    @Autowired
    private DataSourceConfigService service;

    @Autowired
    private DriverJarService driverService;

    // ==================== 查询列表 ====================

    @RequestMapping("/list")
    @ResponseBody
    public String list() {
        try {
            List<DataSourceConfig> list = service.listAll();
            log.debug("查询数据源列表: {} 个", list.size());
            return JsonUtil.toJson(list);
        } catch (Exception e) {
            log.error("查询数据源列表失败, 原因={}", rootCauseMessage(e), e);
            return error("查询数据源列表失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 保存（核心修复） ====================

    @RequestMapping("/save")
    @ResponseBody
    public String save(@RequestBody DataSourceConfig config) {
        if (config == null) {
            log.warn("保存数据源: 请求体解析为空（可能是 JSON 格式错误）");
            return error("请求体不能为空，请检查 JSON 格式");
        }

        boolean isNew = (config.getId() == null || config.getId().trim().isEmpty());
        log.info("收到保存数据源请求: id={}, name={}, dbType={}, host={}:{}/{}, status={}, 操作={}",
                config.getId(), config.getName(), config.getDbType(),
                config.getHost(), config.getPort(), config.getDatabaseName(),
                config.getStatus(), isNew ? "新增" : "更新");

        // 基本参数校验，避免 SQL 层报 "Column 'xxx' cannot be null"
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            return error("数据源名称 name 不能为空");
        }
        if (config.getHost() == null || config.getHost().trim().isEmpty()) {
            return error("主机 host 不能为空");
        }
        if (config.getPort() == null) {
            return error("端口 port 不能为空");
        }
        if (config.getDatabaseName() == null || config.getDatabaseName().trim().isEmpty()) {
            return error("数据库名 databaseName 不能为空");
        }

        try {
            boolean ok = service.save(config);
            if (ok) {
                log.info("保存数据源成功: id={}, name={}, 操作={}",
                        config.getId(), config.getName(), isNew ? "新增" : "更新");
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("id", config.getId());
                return JsonUtil.toJson(result);
            }

            // ★ 关键：影响行数为 0 时给出可读的 error
            String msg = isNew
                    ? "新增失败：INSERT 影响行数为 0（请检查表结构与字段约束）"
                    : "更新失败：id=" + config.getId() + " 的记录不存在（影响行数为 0）";
            log.warn("保存数据源未生效: id={}, name={}, 操作={}, 原因={}",
                    config.getId(), config.getName(), isNew ? "新增" : "更新", msg);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", msg);
            return JsonUtil.toJson(result);

        } catch (Exception e) {
            log.error("保存数据源异常: id={}, name={}, 操作={}, 原因={}",
                    config.getId(), config.getName(), isNew ? "新增" : "更新",
                    rootCauseMessage(e), e);
            return error("保存数据源失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 删除 ====================

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            log.warn("删除数据源: id 为空");
            return error("id 不能为空");
        }
        try {
            boolean ok = service.delete(id);
            return JsonUtil.toJson(Map.of("success", ok));
        } catch (Exception e) {
            log.error("删除数据源失败: id={}, 原因={}", id, rootCauseMessage(e), e);
            return error("删除数据源失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 测试连接 ====================

    @RequestMapping("/test")
    @ResponseBody
    public String testConnection(@RequestBody DataSourceConfig config) {
        if (config == null) {
            log.warn("测试连接: 请求体为空");
            return error("请求体不能为空");
        }
        try {
            boolean ok = service.testConnection(config);
            Map<String, Object> result = new HashMap<>();
            result.put("success", ok);
            result.put("message", ok ? "连接成功" : "连接失败，请查看服务端日志获取详细原因");
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            log.error("测试连接异常, 原因={}", rootCauseMessage(e), e);
            return error("测试连接异常: " + rootCauseMessage(e));
        }
    }

    // ==================== 驱动列表（供前端下拉） ====================

    @RequestMapping("/drivers")
    @ResponseBody
    public String listDrivers() {
        try {
            return JsonUtil.toJson(driverService.listAll());
        } catch (Exception e) {
            log.error("查询驱动列表失败, 原因={}", rootCauseMessage(e), e);
            return error("查询驱动列表失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 工具 ====================

    private String error(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", msg);
        return JsonUtil.toJson(result);
    }

    private String rootCauseMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + ": " + (msg == null ? "(no message)" : msg);
    }
}