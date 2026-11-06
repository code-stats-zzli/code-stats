package com.omni.business.assistance.table.mysql.controller;

import com.omni.business.assistance.table.mysql.service.TableDataService;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.PagingSupport;
import com.omni.framework.jdbc.metedata.datamodel.DataModel;
import com.omni.framework.jdbc.metedata.service.DatabaseMetadataService;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dbclient1")
public class DatabaseClientController extends BaseController {

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
            for (int i = 0; i < dm.getRowCount(); i++) allDbs.add(dm.getRow(i).getValue(0).toString());
            List<String> userDbs = allDbs.stream()
                    .filter(db -> !db.equalsIgnoreCase("information_schema") && !db.equalsIgnoreCase("mysql")
                            && !db.equalsIgnoreCase("performance_schema") && !db.equalsIgnoreCase("sys"))
                    .collect(Collectors.toList());
            return JsonUtil.toJson(userDbs);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/api/tables")
    @ResponseBody
    public String apiTables() {
        String dbName = ParamUtils.parseQueryParams().get("db");
        if (dbName == null || dbName.isEmpty()) return error("缺少参数: db");
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
            return error(e.getMessage());
        }
    }

    @RequestMapping("/api/columns")
    @ResponseBody
    public String apiColumns() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dbName = p.get("db");
        String tableName = p.get("table");
        if (dbName == null || tableName == null) return error("缺少参数: db 或 table");
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
            return error(e.getMessage());
        }
    }

    @RequestMapping("/api/tableData")
    @ResponseBody
    public String getTableData() {
        Map<String, String> p = ParamUtils.parseQueryParams();
        String db = p.get("db");
        String table = p.get("table");
        if (db == null || table == null) return error("缺少参数 db 或 table");
        PagingSupport.Page pg = PagingSupport.fromMap(p, 20, 200);
        try {
            return JsonUtil.toJson(tableDataService.getTableData(db, table, pg.getPage(), pg.getPageSize()));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/api/sqlQuery")
    @ResponseBody
    public String executeSql(@RequestBody Map<String, Object> req) {
        String db = (String) req.get("db");
        String sql = (String) req.get("sql");
        if (db == null || sql == null) return error("缺少参数 db 或 sql");
        try {
            return JsonUtil.toJson(tableDataService.executeCustomSql(db, sql));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}