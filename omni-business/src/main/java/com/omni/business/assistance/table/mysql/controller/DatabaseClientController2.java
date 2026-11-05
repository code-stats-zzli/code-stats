package com.omni.business.assistance.table.mysql.controller;

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
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;

import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

import static com.omni.framework.util.ParamUtils.parseQueryParams;

@Controller
@RequestMapping("/dbclient")
public class DatabaseClientController2 {

    @Autowired
    private DataSourceConfigService configService;

    // 工具方法：从请求中提取 dsId
    private String extractDsId() {
        HttpServletRequest request = RequestContextHolder.getRequest();
        if (request == null) return null;
        String query = request.getQueryString();
        if (query == null) return null;
        Map<String, String> params = parseQueryString(query);
        return params.get("dsId");
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
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

    private DatabaseMetadataService getMetadataService(String dsId) throws Exception {
        if (dsId == null || dsId.isEmpty()) {
            throw new RuntimeException("请选择数据源");
        }
        DataSourceConfig config = configService.getById(dsId);
        if (config == null) throw new RuntimeException("数据源不存在: " + dsId);
        SimpleDataSource dataSource = DynamicDataSourceManager.getOrCreateDataSource(config);
        return new DynamicDatabaseMetadataService(dataSource);
    }

    private DynamicTableDataService getTableDataService(String dsId) throws Exception {
        if (dsId == null || dsId.isEmpty()) {
            throw new RuntimeException("请选择数据源");
        }
        DataSourceConfig config = configService.getById(dsId);
        if (config == null) throw new RuntimeException("数据源不存在: " + dsId);
        SimpleDataSource dataSource = DynamicDataSourceManager.getOrCreateDataSource(config);
        return new DynamicTableDataService(dataSource);
    }

    // 内部类：基于动态数据源的元数据服务
    private static class DynamicDatabaseMetadataService extends AbatractDatabaseMetadataService {
        private final SimpleDataSource dataSource;
        public DynamicDatabaseMetadataService(SimpleDataSource dataSource) {
            this.dataSource = dataSource;
        }
        @Override
        protected java.sql.Connection getConnection() throws Exception {
            return dataSource.getConnection();
        }
    }

    // 内部类：动态表数据服务
    private static class DynamicTableDataService {
        private final com.omni.framework.jdbc.JdbcTemplate jdbcTemplate;
        private final com.omni.framework.jdbc.DynamicSqlExecutor sqlExecutor;
        public DynamicTableDataService(SimpleDataSource dataSource) {
            this.jdbcTemplate = new com.omni.framework.jdbc.JdbcTemplate() {
                @Override
                public java.sql.Connection getConnection() throws java.sql.SQLException {
                    return dataSource.getConnection();
                }
            };
            this.sqlExecutor = new com.omni.framework.jdbc.DynamicSqlExecutor(this.jdbcTemplate);
        }
        public Map<String, Object> getTableData(String database, String tableName, int page, int pageSize) {
            try (java.sql.Connection conn = jdbcTemplate.getConnection()) {
                conn.setCatalog(database);
            } catch (Exception e) {
                throw new RuntimeException("切换数据库失败: " + database, e);
            }
            Map<String, Object> params = new HashMap<>();
            params.put("tableName", tableName);
            String sql = "SELECT * FROM #{tableName}";
            try {
                return sqlExecutor.executePageQuery(sql, params, page, pageSize);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        public List<Map<String, Object>> executeCustomSql(String database, String sql) {
            try (java.sql.Connection conn = jdbcTemplate.getConnection()) {
                conn.setCatalog(database);
            } catch (Exception e) {
                throw new RuntimeException("切换数据库失败: " + database, e);
            }
            String trimmed = sql.trim().toLowerCase();
            if (!trimmed.startsWith("select") && !trimmed.startsWith("show") && !trimmed.startsWith("describe")) {
                throw new RuntimeException("仅支持SELECT/SHOW/DESCRIBE查询语句");
            }
            try {
                return jdbcTemplate.queryForMapList(sql);
            } catch (Exception e) {
                throw new RuntimeException("SQL执行失败: " + e.getMessage(), e);
            }
        }
    }

    // ==================== API 接口 ====================

    @RequestMapping("/api/databases")
    @ResponseBody
    public String apiDatabases() {
        String dsId = extractDsId();
        try {
            DatabaseMetadataService service = getMetadataService(dsId);
            DataModel dm = service.getDatabasesAsDataModel();
            List<String> allDbs = new ArrayList<>();
            for (int i = 0; i < dm.getRowCount(); i++) {
                allDbs.add(dm.getRow(i).getValue(0).toString());
            }
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
        String dsId = extractDsId();
        if (dbName == null || dbName.isEmpty()) {
            return buildErrorJson("缺少参数: db");
        }
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
            return buildErrorJson(e.getMessage());
        }
    }

    // 新增：获取数据源下所有表（跨数据库）
    @RequestMapping("/api/tables/all")
    @ResponseBody
    public String apiAllTables() {
        String dsId = extractDsId();
        if (dsId == null || dsId.isEmpty()) {
            return buildErrorJson("请选择数据源");
        }
        try {
            DatabaseMetadataService service = getMetadataService(dsId);
            List<DatabaseInfo> databases = service.getDatabases();
            List<Map<String, Object>> allTables = new ArrayList<>();
            for (DatabaseInfo db : databases) {
                String dbName = db.getName();
                if (dbName.equalsIgnoreCase("information_schema") ||
                        dbName.equalsIgnoreCase("mysql") ||
                        dbName.equalsIgnoreCase("performance_schema") ||
                        dbName.equalsIgnoreCase("sys")) {
                    continue;
                }
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
            return buildErrorJson(e.getMessage());
        }
    }

    @RequestMapping("/api/columns")
    @ResponseBody
    public String apiColumns() {
        String dbName = getQueryParam("db");
        String tableName = getQueryParam("table");
        String dsId = extractDsId();
        if (dbName == null || tableName == null) {
            return buildErrorJson("缺少参数: db 或 table");
        }
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
            return buildErrorJson(e.getMessage());
        }
    }

    @RequestMapping("/api/tableData")
    @ResponseBody
    public String getTableData() {
        Map<String, String> params = parseQueryParams();
        String db = params.get("db");
        String table = params.get("table");
        String dsId = extractDsId();
        if (db == null || table == null) {
            return buildErrorJson("缺少参数 db 或 table");
        }
        int page = 1, pageSize = 20;
        try {
            page = Integer.parseInt(params.getOrDefault("page", "1"));
            pageSize = Integer.parseInt(params.getOrDefault("pageSize", "20"));
            if (page < 1) page = 1;
            if (pageSize < 1 || pageSize > 200) pageSize = 20;
        } catch (NumberFormatException ignored) {}
        try {
            DynamicTableDataService service = getTableDataService(dsId);
            Map<String, Object> result = service.getTableData(db, table, page, pageSize);
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
        String dsId = extractDsId();
        if (db == null || sql == null) {
            return buildErrorJson("缺少参数 db 或 sql");
        }
        try {
            DynamicTableDataService service = getTableDataService(dsId);
            List<Map<String, Object>> result = service.executeCustomSql(db, sql);
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
        if (query == null) return null;
        Map<String, String> params = parseQueryString(query);
        return params.get(name);
    }
}