package com.omni.business.assistance.connection.controller;

import com.omni.business.assistance.connection.service.ConnectionStringParser;
import com.omni.business.assistance.connection.service.TempConnectionManager;
import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.*;

@Controller
@RequestMapping("/connection/api")
public class ConnectionManagerController {
    private static final Logger log = LoggerFactory.getLogger(ConnectionManagerController.class);

    @Autowired
    private TempConnectionManager tempConnectionManager;

    @Autowired
    private DataSourceConfigService configService;

    /** 三种数据库的默认测试连接串 */
    @RequestMapping("/samples")
    @ResponseBody
    public String samples() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> mysql = new LinkedHashMap<>();
        mysql.put("dbType", "mysql");
        mysql.put("label", "MySQL");
        mysql.put("host", "localhost");
        mysql.put("port", 3306);
        mysql.put("databaseName", "test");
        mysql.put("username", "root");
        mysql.put("url", "jdbc:mysql://localhost:3306/test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        list.add(mysql);

        Map<String, Object> oracle = new LinkedHashMap<>();
        oracle.put("dbType", "oracle");
        oracle.put("label", "Oracle");
        oracle.put("host", "localhost");
        oracle.put("port", 1521);
        oracle.put("databaseName", "ORCLPDB1");
        oracle.put("username", "system");
        oracle.put("url", "jdbc:oracle:thin:@//localhost:1521/ORCLPDB1");
        list.add(oracle);

        Map<String, Object> pg = new LinkedHashMap<>();
        pg.put("dbType", "postgresql");
        pg.put("label", "PostgreSQL");
        pg.put("host", "localhost");
        pg.put("port", 5432);
        pg.put("databaseName", "postgres");
        pg.put("username", "postgres");
        pg.put("url", "jdbc:postgresql://localhost:5432/postgres");
        list.add(pg);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("samples", list);
        return JsonUtil.toJson(r);
    }

    @RequestMapping("/parse")
    @ResponseBody
    public String parse(@RequestBody Map<String, Object> req) {
        String url = req == null ? null : str(req.get("url"));
        Map<String, Object> parsed = ConnectionStringParser.parse(url);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", Boolean.TRUE.equals(parsed.get("valid")));
        r.put("data", parsed);
        if (!Boolean.TRUE.equals(parsed.get("valid"))) {
            r.put("error", parsed.get("message"));
        }
        return JsonUtil.toJson(r);
    }

    @RequestMapping("/test")
    @ResponseBody
    public String test(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        String url = str(req.get("url"));
        String username = str(req.get("username"));
        String password = str(req.get("password"));
        String dbType = str(req.get("dbType"));
        String driverClass = str(req.get("driverClass"));
        String version = str(req.get("version"));

        try {
            TempConnectionManager.TempConn tc = tempConnectionManager.open(
                    url, username, password, dbType, driverClass, version);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", true);
            r.put("tempId", tc.id);
            r.put("dbType", tc.dbType);
            r.put("url", tc.url);
            r.put("message", "连接成功，可执行 SQL 或保存为正式数据源");
            return JsonUtil.toJson(r);
        } catch (Exception e) {
            log.error("测试连接失败: url={}", maskUrl(url), e);
            return error("连接失败: " + rootCauseMessage(e));
        }
    }

    @RequestMapping("/execute")
    @ResponseBody
    public String execute(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        String tempId = str(req.get("tempId"));
        String sql = str(req.get("sql"));
        if (tempId == null || tempId.isEmpty()) return error("缺少 tempId");
        if (sql == null || sql.trim().isEmpty()) return error("SQL 不能为空");
        try {
            Map<String, Object> result = tempConnectionManager.execute(tempId, sql);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", true);
            r.putAll(result);
            return JsonUtil.toJson(r);
        } catch (SecurityException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("临时 SQL 执行失败", e);
            return error("SQL 执行失败: " + rootCauseMessage(e));
        }
    }

    @RequestMapping("/close")
    @ResponseBody
    public String close(@RequestBody Map<String, Object> req) {
        String tempId = req == null ? null : str(req.get("tempId"));
        if (tempId == null || tempId.isEmpty()) return error("缺少 tempId");
        tempConnectionManager.close(tempId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("message", "临时连接已关闭");
        return JsonUtil.toJson(r);
    }

    /** 保存为正式数据源 */
    @RequestMapping("/save")
    @ResponseBody
    public String save(@RequestBody Map<String, Object> req) {
        if (req == null) return error("请求体为空");
        String tempId = str(req.get("tempId"));
        String name = str(req.get("name"));
        if (tempId == null || tempId.isEmpty()) return error("缺少 tempId");
        TempConnectionManager.TempConn tc = tempConnectionManager.get(tempId);
        if (tc == null) return error("临时连接不存在或已关闭");

        Map<String, Object> parsed = ConnectionStringParser.parse(tc.url);
        if (!Boolean.TRUE.equals(parsed.get("valid"))) return error("连接串无法解析，无法保存");

        String host = str(parsed.get("host"));
        Integer port = parsed.get("port") instanceof Number
                ? ((Number) parsed.get("port")).intValue() : null;
        String databaseName = str(parsed.get("databaseName"));

        if (name == null || name.trim().isEmpty()) {
            name = tc.dbType + "_" + host + "_" + (databaseName == null ? "" : databaseName);
        }

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(null);
        cfg.setName(name.trim());
        cfg.setDbType(tc.dbType);
        cfg.setHost(host);
        cfg.setPort(port);
        cfg.setDatabaseName(databaseName);
        cfg.setUsername(tc.username);
        cfg.setPassword(tc.password);
        cfg.setDriverClass(tc.driverClass);
        cfg.setVersion(tc.version);
        cfg.setStatus(1);

        try {
            boolean ok = configService.save(cfg);
            if (!ok) return error("保存失败：数据库影响行数为 0");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", true);
            r.put("id", cfg.getId());
            r.put("message", "已保存为正式数据源");
            return JsonUtil.toJson(r);
        } catch (Exception e) {
            log.error("保存连接失败", e);
            return error("保存失败: " + rootCauseMessage(e));
        }
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }

    private String error(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("error", msg);
        return JsonUtil.toJson(r);
    }

    private String rootCauseMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + ": " + (msg == null ? "(no message)" : msg);
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        int idx = url.indexOf("://");
        if (idx > 0 && url.length() > idx + 8) return url.substring(0, idx + 3) + "***";
        return url;
    }
}