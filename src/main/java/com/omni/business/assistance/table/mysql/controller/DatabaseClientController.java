package com.omni.business.assistance.table.mysql.controller;

import com.omni.framework.jdbc.metedata.datamodel.DataModel;
import com.omni.framework.jdbc.metedata.service.DatabaseMetadataService;

import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.util.JsonUtil;

import java.net.URLDecoder;
import java.util.*;

@Controller
@RequestMapping("/dbclient")
public class DatabaseClientController {

    @Autowired
    private DatabaseMetadataService metadataService;

    @RequestMapping("/api/databases")
    @ResponseBody
    public String apiDatabases() {
        try {
            DataModel dm = metadataService.getDatabasesAsDataModel();
            // 只返回数据库名称列表
            List<String> dbNames = new ArrayList<>();
            for (int i = 0; i < dm.getRowCount(); i++) {
                dbNames.add(dm.getRow(i).getValue(0).toString());
            }
            return JsonUtil.toJson(dbNames);
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
            // 只返回表名和类型两个字段
            Set<String> columns = new LinkedHashSet<>(Arrays.asList("TABLE_NAME", "TABLE_TYPE"));
            // 注意：DataModelConverter.rowsToJsonArray 返回的是 JSON 数组字符串，其中每行的键是原始列名
            // 前端期望 name/type，因此我们可以用别名映射，这里直接转换后通过 DataModelConverter 自定义列实现
            // 但 DataModelConverter 直接使用列名输出，为匹配前端，我们用 rowsToJsonArray 全字段再手动改？不，前端已适配。
            // 实际上前端 fetch 后直接访问 .name 和 .type，所以我们需要将列名映射一下。
            // 最简单的方式：直接构建 List<Map> 并序列化。
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
            // 手动构建返回列表，字段名使用驼峰，与前端 columnMapping 一致
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

    private String buildErrorJson(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        return JsonUtil.toJson(error);
    }

    private String getQueryParam(String name) {
        Request request = RequestContextHolder.getRequest();
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