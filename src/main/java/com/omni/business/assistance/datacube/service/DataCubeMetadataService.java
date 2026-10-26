package com.omni.business.assistance.datacube.service;

import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.common.datasource.DynamicDataSourceManager;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.framework.datacube.model.ColumnMeta;
import com.omni.framework.datacube.model.CubeSchema;
import com.omni.framework.jdbc.metedata.model.ColumnInfo;
import com.omni.framework.jdbc.metedata.model.DatabaseInfo;
import com.omni.framework.jdbc.metedata.model.TableInfo;
import com.omni.framework.jdbc.metedata.service.AbatractDatabaseMetadataService;
import com.omni.framework.pool.SimpleDataSource;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 元数据服务：数据源 / 数据库 / 表 / 列 探测
 * 复用现有数据源管理 + AbatractDatabaseMetadataService
 */
@Service
public class DataCubeMetadataService {

    @Autowired
    private DataSourceConfigService configService;

    /** 数据源列表（精简） */
    public List<Map<String, Object>> listDataSources() throws Exception {
        List<DataSourceConfig> list = configService.listAll();
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (DataSourceConfig c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("dbType", c.getDbType());
            m.put("databaseName", c.getDatabaseName());
            out.add(m);
        }
        return out;
    }

    /** 数据库列表 */
    public List<String> listDatabases(String dsId) throws Exception {
        AbatractDatabaseMetadataService meta = newMetadata(dsId);
        List<DatabaseInfo> dbs = meta.getDatabases();
        List<String> out = new ArrayList<>(dbs.size());
        for (DatabaseInfo d : dbs) out.add(d.getName());
        return out;
    }

    /** 表列表（只返回 TABLE 类型，过滤系统库） */
    public List<Map<String, Object>> listTables(String dsId, String database) throws Exception {
        AbatractDatabaseMetadataService meta = newMetadata(dsId);
        List<TableInfo> tables = meta.getTables(database, null, "%");
        List<Map<String, Object>> out = new ArrayList<>(tables.size());
        for (TableInfo t : tables) {
            String type = t.getType();
            if (type != null && !"TABLE".equalsIgnoreCase(type)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.getName());
            m.put("type", t.getType());
            m.put("remarks", t.getRemarks());
            out.add(m);
        }
        return out;
    }

    /** 列元数据（返回 CubeSchema 结构） */
    public CubeSchema describeColumns(String dsId, String database, String table) throws Exception {
        AbatractDatabaseMetadataService meta = newMetadata(dsId);
        List<ColumnInfo> columns = meta.getColumns(database, null, table);
        CubeSchema schema = new CubeSchema();
        schema.setColumns(columns.stream()
                .map(ColumnMeta::from)
                .collect(Collectors.toList()));
        schema.getExtra().put("source", "table");
        schema.getExtra().put("database", database);
        schema.getExtra().put("table", table);
        return schema;
    }

    private AbatractDatabaseMetadataService newMetadata(String dsId) throws Exception {
        DataSourceConfig config = configService.getById(dsId);
        if (config == null) throw new IllegalArgumentException("数据源不存在: " + dsId);
        SimpleDataSource ds = DynamicDataSourceManager.getOrCreateDataSource(config);
        return new AbatractDatabaseMetadataService() {
            @Override protected Connection getConnection() throws Exception {
                return ds.getConnection();
            }
        };
    }
}