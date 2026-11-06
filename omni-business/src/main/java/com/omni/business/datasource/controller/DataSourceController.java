package com.omni.business.datasource.controller;

import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.BizExceptions;
import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.business.datasource.service.DriverJarService;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.common.util.StringUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/datasource/api")
public class DataSourceController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(DataSourceController.class);

    @Autowired
    private DataSourceConfigService service;

    @Autowired
    private DriverJarService driverService;

    @RequestMapping("/list")
    @ResponseBody
    public String list() {
        try {
            List<DataSourceConfig> list = service.listAll();
            log.debug("查询数据源列表: {} 个", list.size());
            return JsonUtil.toJson(list);
        } catch (Exception e) {
            log.error("查询数据源列表失败, 原因={}", BizExceptions.shortMessage(e), e);
            return error("查询数据源列表失败: " + BizExceptions.shortMessage(e));
        }
    }

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

        if (StringUtils.isBlank(config.getName()))         return error("数据源名称 name 不能为空");
        if (StringUtils.isBlank(config.getHost()))         return error("主机 host 不能为空");
        if (config.getPort() == null)                      return error("端口 port 不能为空");
        if (StringUtils.isBlank(config.getDatabaseName())) return error("数据库名 databaseName 不能为空");

        try {
            boolean ok = service.save(config);
            if (ok) {
                log.info("保存数据源成功: id={}, name={}, 操作={}", config.getId(), config.getName(), isNew ? "新增" : "更新");
                return okId(config.getId());
            }
            String msg = isNew
                    ? "新增失败：INSERT 影响行数为 0（请检查表结构与字段约束）"
                    : "更新失败：id=" + config.getId() + " 的记录不存在（影响行数为 0）";
            log.warn("保存数据源未生效: id={}, name={}, 操作={}, 原因={}",
                    config.getId(), config.getName(), isNew ? "新增" : "更新", msg);
            return error(msg);
        } catch (Exception e) {
            log.error("保存数据源异常: id={}, name={}, 操作={}, 原因={}",
                    config.getId(), config.getName(), isNew ? "新增" : "更新",
                    BizExceptions.shortMessage(e), e);
            return error("保存数据源失败: " + BizExceptions.shortMessage(e));
        }
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(String id) {
        if (StringUtils.isBlank(id)) {
            log.warn("删除数据源: id 为空");
            return error("id 不能为空");
        }
        try {
            boolean ok = service.delete(id);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("deleted", ok);
            return okFlat(out);
        } catch (Exception e) {
            log.error("删除数据源失败: id={}, 原因={}", id, BizExceptions.shortMessage(e), e);
            return error("删除数据源失败: " + BizExceptions.shortMessage(e));
        }
    }

    @RequestMapping("/test")
    @ResponseBody
    public String testConnection(@RequestBody DataSourceConfig config) {
        if (config == null) {
            log.warn("测试连接: 请求体为空");
            return error("请求体不能为空");
        }
        try {
            boolean ok = service.testConnection(config);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("connected", ok);
            out.put("message", ok ? "连接成功" : "连接失败，请查看服务端日志获取详细原因");
            return okFlat(out);
        } catch (Exception e) {
            log.error("测试连接异常, 原因={}", BizExceptions.shortMessage(e), e);
            return error("测试连接异常: " + BizExceptions.shortMessage(e));
        }
    }

    @RequestMapping("/drivers")
    @ResponseBody
    public String listDrivers() {
        try {
            return JsonUtil.toJson(driverService.listAll());
        } catch (Exception e) {
            log.error("查询驱动列表失败, 原因={}", BizExceptions.shortMessage(e), e);
            return error("查询驱动列表失败: " + BizExceptions.shortMessage(e));
        }
    }
}