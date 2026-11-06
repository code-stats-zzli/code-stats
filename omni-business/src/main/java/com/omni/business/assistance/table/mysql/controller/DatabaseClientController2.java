package com.omni.business.assistance.table.mysql.controller;

import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.PagingSupport;
import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.business.datasource.service.DynamicDataSourceManager;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.framework.jdbc.metedata.datamodel.DataModel;
import com.omni.framework.jdbc.metedata.model.DatabaseInfo;
import com.omni.framework.jdbc.metedata.model.TableInfo;
import com.omni.framework.jdbc.metedata.service.AbatractDatabaseMetadataService;
import com.omni.framework.jdbc.metedata.service.DatabaseMetadataService;
import com.omni.framework.pool.SimpleDataSource;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dbclient")
public class DatabaseClientController2 extends BaseController {

    @Autowired
    private DataSourceConfigService configService;

    private String extractDsId() {
        return ParamUtils.parseQueryParams().get("dsId");
    }

    private DatabaseMetadataService getMetadataService(String dsId) throws Exception {
        if (dsId == null || dsId.isEmpty()) throw new RuntimeException("请选择数据源");
        DataSourceConfig config = configService.getById(dsId);
        if (config == null) throw new RuntimeException("数据源不存在: " + dsId);
        SimpleDataSource dataSource = DynamicDataSourceManager.getOrCreateDataSource(config);
        return new DynamicDatabaseMetadataService(dataSource);
    }

    private DynamicTableDataService getTableDataService(String dsId) throws Exception {
        if (dsId == null || dsId.isEmpty()) throw new RuntimeException("请选择数据源");
        DataSourceConfig config = configService.getById(dsId);
        if (config == null) throw new RuntimeException("数据源不存在: " + dsId);
        SimpleDataSource dataSource = DynamicDataSourceManager.getOrCreateDataSource(config);
        return new DynamicTableDataService(dataSource);
    }

    private static class DynamicDatabaseMetadataService extends AbatractDatabaseMetadataService {
        private final SimpleDataSource dataSource;
        public DynamicDatabaseMetadataService(SimpleDataSource ds) { this.dataSource = ds; }
        @Override protected java.sql.Connection getConnection() throws Exception { return dataSource.getConnection(); }
    }

    /** ★ L2-03：内部类改用 withCatalog 修 bug */
    private static class DynamicTableDataService {
        private final com.omni.framework.jdbc.JdbcTemplate jdbcTemplate;
        private final com.omni.framework.jdbc.DynamicSqlExecutor sqlExecutor;
        public DynamicTableDataService(SimpleDataSource ds) {
            this.jdbcTemplate = new com.omni.framework.jdbc.JdbcTemplate() {
                @Override public java.sql.Connection getConnection() throws java.sql.SQLException { return ds.getConnection(); }
            };
            this.sqlExecutor = new com.omni.framework.jdbc.DynamicSqlExecutor(this.jdbcTemplate);
        }

        public Map<String, Object> getTableData(String database, String tableName, int page, int pageSize) {
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            String sql = "SELECT * FROM #{tableName}";
            try {
                return jdbcTemplate.withCatalog(database, conn -> {
                    try { return sqlExecutor.executePageQuery(sql, params, page, pageSize); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
            } catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new RuntimeException("切换数据库失败: " + database, e); }
        }

        public List<Map<String, Object>> executeCustomSql(String database, String sql) {
            String trimmed = sql.trim().toLowerCase();
            if (!trimmed.startsWith("select") && !trimmed.startsWith("show") && !trimmed.startsWith("describe")) {
                throw new RuntimeException("仅支持SELECT/SHOW/DESCRIBE查询语句");
            }
            try {
                return jdbcTemplate.withCatalog(database, conn -> {
                    try { return jdbcTemplate.queryForMapList(sql); }
                    catch (Exception e) { throw new RuntimeException("SQL执行失败: " + e.getMessage(), e); }
                });
            } catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new RuntimeException("切换数据库失败: " + database, e); }
        }
    }

    // ==================== API ====================

    @RequestMapping("/api/databases")
    @ResponseBody
    public String apiDatabases() {
        String dsId = extractDsId();
        try {
            DatabaseMetadataService service = getMetadataService(dsId);
            DataModel dm = service.getDatabasesAsDataModel();
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
        Map<String, String> p = ParamUtils.parseQueryParams();
        String dbName = p.get("db");
        String dsId = p.get("dsId");
        if (dbName == null || dbName.isEmpty()) return error("缺少参数: db");
        try {
            DatabaseMetadataService service = getMetadataService(dsId);
            DataModel dm = service.getTablesAsDataModel(dbName, null, "%");
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

    @RequestMapping("/api/tables/all")
    @ResponseBody
    public String apiAllTables() {
        String dsId = extractDsId();
        if (dsId == null || dsId.isEmpty()) return error("请选择数据源");
        try {
            DatabaseMetadataService service = getMetadataService(dsId);
            List<DatabaseInfo> databases = service.getDatabases();
            List<Map<String, Object>> allTables = new ArrayList<>();
            for (DatabaseInfo db : databases) {
                String dbName = db.getName();
                if (dbName.equalsIgnoreCase("information_schema") || dbName.equalsIgnoreCase("mysql")
                        || dbName.equalsIgnoreCase("performance_schema") || dbName.equalsIgnoreCase("sys")) continue;
                List<TableInfo> tables = service.getTables(dbName, null, "%");
                for (TableInfo table : tables) {
                    if (!"TABLE".equalsIgnoreCase(table.getType())) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("database", dbName);
                    item.put("name", table.getName());
                    item.put("type", table.getType());
                    allTables.add(item);
                }
            }
            return JsonUtil.toJson(allTables);
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
        String dsId = p.get("dsId");
        if (dbName == null || tableName == null) return error("缺少参数: db 或 table");
        try {
            DatabaseMetadataService service = getMetadataService(dsId);
            DataModel dm = service.getColumnsAsDataModel(dbName, null, tableName);
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
            DynamicTableDataService service = getTableDataService(p.get("dsId"));
            return JsonUtil.toJson(service.getTableData(db, table, pg.getPage(), pg.getPageSize()));
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
            DynamicTableDataService service = getTableDataService(extractDsId());
            return JsonUtil.toJson(service.executeCustomSql(db, sql));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}