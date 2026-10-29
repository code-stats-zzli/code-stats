package com.omni.business.assistance.datacube.provider;

import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.common.datasource.DynamicDataSourceManager;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.common.util.SecurityUtil;
import com.omni.framework.datacube.model.*;
import com.omni.framework.datacube.spi.CubeProvider;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.jdbc.dialect.SqlDialect;
import com.omni.framework.jdbc.dialect.SqlDialectRegistry;
import com.omni.framework.jdbc.metedata.model.ColumnInfo;
import com.omni.framework.jdbc.metedata.service.AbatractDatabaseMetadataService;
import com.omni.framework.pool.SimpleDataSource;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/** Table 数据来源 Provider — 支持多方言 */
@Service
public class TableCubeProvider implements CubeProvider {

    @Autowired
    private DataSourceConfigService configService;

    @Autowired
    private SqlDialectRegistry dialectRegistry;

    @Override
    public CubeType getType() { return CubeType.TABLE; }

    @Override
    @SuppressWarnings("unchecked")
    public CubeQueryResult query(DataCube cube, Map<String, Object> params) throws Exception {
        String dsId = getString(cube.getConfig(), "datasourceId");
        String database = getString(cube.getConfig(), "database");
        String table = getString(cube.getConfig(), "table");
        List<String> wantColumns = (List<String>) cube.getConfig().get("columns");

        if (dsId == null || database == null || table == null)
            throw new IllegalArgumentException("config 缺少 datasourceId/database/table");
        if (!SecurityUtil.isValidTableName(table))
            throw new SecurityException("非法表名: " + table);

        int page = toInt(params.get("page"), 1);
        int pageSize = toInt(params.get("pageSize"), 20);
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 1000) pageSize = 20;

        DataSourceConfig dsConfig = configService.getById(dsId);
        if (dsConfig == null) throw new IllegalArgumentException("数据源不存在: " + dsId);

        SqlDialect dialect = resolveDialect(dsConfig.getDbType());

        SimpleDataSource ds = openDataSource(dsConfig);
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public Connection getConnection() throws SQLException {
                return ds.getConnection();
            }
        };

        // ★ 使用方言的标识符引用
        String qualified = dialect.quote(database) + "." + dialect.quote(table);
        String selectCols;
        if (wantColumns != null && !wantColumns.isEmpty()) {
            selectCols = wantColumns.stream()
                    .map(dialect::quote)
                    .collect(Collectors.joining(", "));
        } else {
            selectCols = "*";
        }

        long start = System.currentTimeMillis();
        int offset = (page - 1) * pageSize;

        long total;
        try {
            total = jdbc.queryOne("SELECT COUNT(*) FROM " + qualified, rs -> rs.getLong(1));
        } catch (Exception e) {
            total = -1;
        }

        // ★ 使用方言分页
        String baseSql = "SELECT " + selectCols + " FROM " + qualified;
        String sql = dialect.buildPageSql(baseSql, offset, pageSize);
        List<Map<String, Object>> records = jdbc.queryForMapList(sql);

        List<String> columns;
        if (wantColumns != null && !wantColumns.isEmpty()) {
            columns = new ArrayList<>(wantColumns);
        } else if (!records.isEmpty()) {
            columns = new ArrayList<>(records.get(0).keySet());
        } else {
            columns = Collections.emptyList();
        }

        List<List<Object>> rows = new ArrayList<>(records.size());
        for (Map<String, Object> rec : records) {
            List<Object> row = new ArrayList<>(columns.size());
            for (String c : columns) row.add(rec.get(c));
            rows.add(row);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("total", total);
        meta.put("page", page);
        meta.put("pageSize", pageSize);
        meta.put("source", "table:" + database + "." + table);
        meta.put("dialect", dialect.getName());
        meta.put("durationMs", System.currentTimeMillis() - start);

        return CubeQueryResult.ok(columns, rows, meta);
    }

    @Override
    public CubeSchema describe(DataCube cube) throws Exception {
        String dsId = getString(cube.getConfig(), "datasourceId");
        String database = getString(cube.getConfig(), "database");
        String table = getString(cube.getConfig(), "table");
        if (dsId == null || database == null || table == null)
            throw new IllegalArgumentException("config 缺少 datasourceId/database/table");

        SimpleDataSource ds = openDataSource(configService.getById(dsId));
        AbatractDatabaseMetadataService metaService = new AbatractDatabaseMetadataService() {
            @Override protected Connection getConnection() throws Exception {
                return ds.getConnection();
            }
        };

        List<ColumnInfo> columnInfos = metaService.getColumns(database, null, table);
        CubeSchema schema = new CubeSchema();
        schema.setColumns(columnInfos.stream()
                .map(ColumnMeta::from)
                .collect(Collectors.toList()));
        schema.getExtra().put("source", "table");
        schema.getExtra().put("database", database);
        schema.getExtra().put("table", table);
        return schema;
    }

    private SqlDialect resolveDialect(String dbType) {
        if (dialectRegistry != null) {
            SqlDialect d = dialectRegistry.getOrMysql(dbType);
            if (d != null) return d;
        }
        return new com.omni.framework.jdbc.dialect.MysqlDialect();
    }

    private SimpleDataSource openDataSource(DataSourceConfig config) throws Exception {
        if (config == null) throw new IllegalArgumentException("数据源不存在");
        return DynamicDataSourceManager.getOrCreateDataSource(config);
    }

    private static String getString(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static int toInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }
}