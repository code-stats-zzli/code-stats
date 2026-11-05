package com.omni.business.assistance.table.mysql.controller;

import com.omni.business.assistance.table.mysql.service.TableDataService;
import com.omni.framework.jdbc.metedata.datamodel.DataModel;
import com.omni.framework.jdbc.metedata.service.DatabaseMetadataService;

import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;

import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

import static com.omni.framework.util.ParamUtils.parseQueryParams;

@Controller
@RequestMapping("/dbclient1")
public class DatabaseClientController {

    @Autowired
    private DatabaseMetadataService metadataService;

    @Autowired
    private TableDataService tableDataService;

    @RequestMapping("/api/databases")
    @ResponseBody
    public String apiDatabases() {
        try {
            DataModel dm = metadataService.getDatabasesAsDataModel();
            List<String> allDbs = new ArrayList<>();
            for (int i = 0; i < dm.getRowCount(); i++) {
                allDbs.add(dm.getRow(i).getValue(0).toString());
            }
            // 过滤系统数据库（只保留用户数据库）
            List<String> userDbs = allDbs.stream()
                    .filter(db -> !db.equalsIgnoreCase("information_schema")
                            && !db.equalsIgnoreCase("mysql")
                            && !db.equalsIgnoreCase("performance_schema")
                            && !db.equalsIgnoreCase("sys"))
                    .collect(Collectors.toList());
            return JsonUtil.toJson(userDbs);
        } catch (Exception e) {
            return buildErrorJson(e.getMessage());
        }
    }

    @RequestMapping("/api/tables")
    @ResponseBody
    public String apiTables() {
        String dbName = getQueryParam("db");
        if (dbName == null || dbName.isEmpty()) {
            return buildErrorJson("Missing parameter: db");
        }
        try {
            DataModel dm = metadataService.getTablesAsDataModel(dbName, null, "%");
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < dm.getRowCount(); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", dm.getRow(i).getValue("TABLE_NAME"));
                item.put("type", dm.getRow(i).getValue("TABLE_TYPE"));
                list.add(item);
            }
            return JsonUtil.toJson(list);
        } catch (Exception e) {
            return buildErrorJson(e.getMessage());
        }
    }

    @RequestMapping("/api/columns")
    @ResponseBody
    public String apiColumns() {
        String dbName = getQueryParam("db");
        String tableName = getQueryParam("table");
        if (dbName == null || tableName == null) {
            return buildErrorJson("Missing parameter: db or table");
        }
        try {
            DataModel dm = metadataService.getColumnsAsDataModel(dbName, null, tableName);
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < dm.getRowCount(); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("columnName", dm.getRow(i).getValue("COLUMN_NAME"));
                item.put("typeName", dm.getRow(i).getValue("TYPE_NAME"));
                item.put("columnSize", dm.getRow(i).getValue("COLUMN_SIZE"));
                item.put("nullable", dm.getRow(i).getValue("NULLABLE"));
                item.put("autoIncrement", dm.getRow(i).getValue("IS_AUTOINCREMENT"));
                item.put("remarks", dm.getRow(i).getValue("REMARKS"));
                list.add(item);
            }
            return JsonUtil.toJson(list);
        } catch (Exception e) {
            return buildErrorJson(e.getMessage());
        }
    }

    @RequestMapping("/api/tableData")
    @ResponseBody
    public String getTableData() {
        Map<String, String> params = parseQueryParams();
        String db = params.get("db");
        String table = params.get("table");
        if (db == null || table == null) {
            return buildErrorJson("缺少参数 db 或 table");
        }
        int page = 1;
        int pageSize = 20;
        try {
            page = Integer.parseInt(params.getOrDefault("page", "1"));
            pageSize = Integer.parseInt(params.getOrDefault("pageSize", "20"));
            if (page < 1) page = 1;
            if (pageSize < 1 || pageSize > 200) pageSize = 20;
        } catch (NumberFormatException ignored) {}
        try {
            Map<String, Object> result = tableDataService.getTableData(db, table, page, pageSize);
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            return buildErrorJson(e.getMessage());
        }
    }

    @RequestMapping("/api/sqlQuery")
    @ResponseBody
    public String executeSql(@RequestBody Map<String, Object> req) {
        String db = (String) req.get("db");
        String sql = (String) req.get("sql");
        if (db == null || sql == null) {
            return buildErrorJson("缺少参数 db 或 sql");
        }
        try {
            List<Map<String, Object>> result = tableDataService.executeCustomSql(db, sql);
            return JsonUtil.toJson(result);
        } catch (Exception e) {
            return buildErrorJson(e.getMessage());
        }
    }

    private String buildErrorJson(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        return JsonUtil.toJson(error);
    }

    private String getQueryParam(String name) {
        HttpServletRequest request = RequestContextHolder.getRequest();
        if (request == null) return null;
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) return null;
        Map<String, String> params = parseQueryString(query);
        return params.get(name);
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }
}