// ======== src/main/java/com/omni/business/assistance/apipublisher/service/ApiExecuteService.java ========
package com.omni.business.assistance.apipublisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.business.assistance.apipublisher.model.ApiDefinition;
import com.omni.business.assistance.apipublisher.model.ApiParam;
import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.common.datasource.DynamicDataSourceManager;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.common.util.SecurityUtil;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.jdbc.dialect.MysqlDialect;
import com.omni.framework.jdbc.dialect.SqlDialect;
import com.omni.framework.jdbc.dialect.SqlDialectRegistry;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.pool.SimpleDataSource;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApiExecuteService {
    private static final Logger log = LoggerFactory.getLogger(ApiExecuteService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private ApiPublishService publishService;
    @Autowired private DataSourceConfigService configService;
    @Autowired private SqlDialectRegistry dialectRegistry;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Map<String, Object> execute(String apiId, Map<String, Object> params) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        ApiDefinition def = publishService.getById(apiId);
        if (def == null) {
            result.put("success", false);
            result.put("error", "API 不存在: " + apiId);
            return result;
        }
        if (def.getStatus() == null || def.getStatus() != 1) {
            result.put("success", false);
            result.put("error", "API 已禁用: " + apiId);
            return result;
        }

        String apiType = normalizeType(def);

        result.put("apiId", def.getId());
        result.put("name", def.getName());
        result.put("path", def.getPath());
        result.put("method", def.getMethod());
        result.put("apiType", apiType);

        try {
            Map<String, Object> values = validateInputs(def, params);
            long start = System.currentTimeMillis();
            Object data;
            switch (apiType) {
                case "table":
                    data = executeTable(def, values);
                    break;
                case "system":
                    data = executeSystem(def, values);
                    break;
                case "custom":
                default:
                    data = executeCustom(def, values);
                    break;
            }
            result.put("data", data);
            result.put("success", true);
            result.put("durationMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage());
            log.error("API 执行失败: id={}, type={}", apiId, apiType, e);
        }
        return result;
    }

    /** 兼容旧数据：无 apiType 时从 sourceType 推断 */
    private String normalizeType(ApiDefinition def) {
        String t = def.getApiType();
        if (t != null && !t.isEmpty()) return t;
        String st = def.getSourceType();
        if ("external".equals(st)) return "custom";
        return "table";
    }

    private Map<String, Object> validateInputs(ApiDefinition def, Map<String, Object> params) {
        Map<String, Object> values = new HashMap<>();
        if (def.getInputs() == null) return values;
        for (ApiParam p : def.getInputs()) {
            Object v = params == null ? null : params.get(p.getName());
            boolean empty = v == null || "".equals(v);
            if (empty && p.getDefaultValue() != null && !p.getDefaultValue().isEmpty()) {
                v = p.getDefaultValue();
                empty = false;
            }
            if (empty && p.isRequired()) {
                throw new IllegalArgumentException("缺少必填参数: " + p.getName());
            }
            String key = (p.getMapTo() != null && !p.getMapTo().isEmpty())
                    ? p.getMapTo() : p.getName();
            values.put(key, v);
        }
        return values;
    }

    // ================== table（数据库表查询） ==================
    private Map<String, Object> executeTable(ApiDefinition def, Map<String, Object> inputs) throws Exception {
        DataSourceConfig dsConfig = configService.getById(def.getDatasourceId());
        if (dsConfig == null) throw new IllegalArgumentException("数据源不存在: " + def.getDatasourceId());
        if (!SecurityUtil.isValidTableName(def.getTable())) {
            throw new SecurityException("非法表名: " + def.getTable());
        }

        SqlDialect dialect = dialectRegistry != null
                ? dialectRegistry.getOrMysql(dsConfig.getDbType())
                : new MysqlDialect();

        SimpleDataSource ds = DynamicDataSourceManager.getOrCreateDataSource(dsConfig);
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public Connection getConnection() throws SQLException {
                return ds.getConnection();
            }
        };

        String qualified = dialect.quote(def.getDatabase()) + "." + dialect.quote(def.getTable());

        String selectCols = "*";
        List<String> wantedCols = new ArrayList<>();
        if (def.getOutputs() != null && !def.getOutputs().isEmpty()) {
            StringBuilder cols = new StringBuilder();
            for (int i = 0; i < def.getOutputs().size(); i++) {
                ApiParam o = def.getOutputs().get(i);
                if (!SecurityUtil.isValidColumnName(o.getName())) {
                    throw new SecurityException("非法列名: " + o.getName());
                }
                if (i > 0) cols.append(", ");
                cols.append(dialect.quote(o.getName()));
                wantedCols.add(o.getName());
            }
            selectCols = cols.toString();
        }

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (def.getInputs() != null) {
            for (ApiParam p : def.getInputs()) {
                String column = (p.getMapTo() != null && !p.getMapTo().isEmpty())
                        ? p.getMapTo() : p.getName();
                Object v = inputs.get(column);
                if (v == null || "".equals(v)) continue;
                if (!SecurityUtil.isValidColumnName(column)) continue;
                where.append(where.length() == 0 ? " WHERE " : " AND ");
                where.append(dialect.quote(column)).append(" = ?");
                args.add(v);
            }
        }

        long total = -1;
        try {
            total = jdbc.queryOne("SELECT COUNT(*) FROM " + qualified + where,
                    rs -> rs.getLong(1), args.toArray());
        } catch (Exception ignored) {}

        int page = toInt(inputs.get("page"), 1);
        int pageSize = toInt(inputs.get("pageSize"),
                def.getPageSize() != null ? def.getPageSize() : 20);
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 1000) pageSize = 20;

        String baseSql = "SELECT " + selectCols + " FROM " + qualified + where;
        String sql = (def.getPageEnabled() != null && def.getPageEnabled() == 1)
                ? dialect.buildPageSql(baseSql, (page - 1) * pageSize, pageSize)
                : baseSql;

        List<Map<String, Object>> records = jdbc.queryForMapList(sql, args.toArray());

        List<String> columns = new ArrayList<>();
        if (!wantedCols.isEmpty()) {
            columns.addAll(wantedCols);
        } else if (!records.isEmpty()) {
            columns.addAll(records.get(0).keySet());
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : records) {
            List<Object> row = new ArrayList<>();
            for (String c : columns) row.add(r.get(c));
            rows.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("columns", columns);
        data.put("rows", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    // ================== custom（自定义 HTTP 接口） ==================
    private Map<String, Object> executeCustom(ApiDefinition def, Map<String, Object> inputs) throws Exception {
        if (def.getBackendUrl() == null || def.getBackendUrl().isEmpty()) {
            throw new IllegalArgumentException("未配置后端地址 backendUrl");
        }
        String method = def.getMethod() == null ? "POST" : def.getMethod().toUpperCase();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(def.getBackendUrl()))
                .timeout(Duration.ofSeconds(60));

        // ★ 支持自定义 Headers
        if (def.getHeaders() != null && !def.getHeaders().trim().isEmpty()) {
            try {
                Map<String, Object> hdrMap = MAPPER.readValue(def.getHeaders(), Map.class);
                for (Map.Entry<String, Object> e : hdrMap.entrySet()) {
                    builder.header(e.getKey(), String.valueOf(e.getValue()));
                }
            } catch (Exception e) {
                log.warn("自定义 Headers 解析失败: {}", def.getHeaders(), e);
            }
        }

        if ("GET".equals(method)) {
            // 参数拼到 URL
            StringBuilder url = new StringBuilder(def.getBackendUrl());
            if (!inputs.isEmpty()) {
                url.append(def.getBackendUrl().contains("?") ? "&" : "?");
                boolean first = true;
                for (Map.Entry<String, Object> e : inputs.entrySet()) {
                    if (e.getValue() == null) continue;
                    if (!first) url.append("&");
                    url.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(String.valueOf(e.getValue()), "UTF-8"));
                    first = false;
                }
            }
            builder = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .GET();
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(
                    MAPPER.writeValueAsString(inputs)));
        }

        HttpResponse<String> resp = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", resp.statusCode());
        data.put("body", resp.body());
        return data;
    }

    // ================== system（本地 Controller 接口） ==================
    private Map<String, Object> executeSystem(ApiDefinition def, Map<String, Object> inputs) throws Exception {
        if (def.getSystemPath() == null || def.getSystemPath().isEmpty()) {
            throw new IllegalArgumentException("未配置系统接口路径 systemPath");
        }
        String method = def.getSystemMethod() != null ? def.getSystemMethod().toUpperCase() : "POST";

        String port = ConfigLoader.getProperty("server.port", "28080");
        String contextPath = ConfigLoader.getProperty("context.path", "/omni");
        String base = "http://127.0.0.1:" + port + contextPath + def.getSystemPath();

        URI uri;
        HttpRequest.Builder builder;

        if ("GET".equals(method)) {
            StringBuilder url = new StringBuilder(base);
            if (!inputs.isEmpty()) {
                url.append(base.contains("?") ? "&" : "?");
                boolean first = true;
                for (Map.Entry<String, Object> e : inputs.entrySet()) {
                    if (e.getValue() == null) continue;
                    if (!first) url.append("&");
                    url.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8.name()))
                            .append("=")
                            .append(URLEncoder.encode(String.valueOf(e.getValue()),
                                    StandardCharsets.UTF_8.name()));
                    first = false;
                }
            }
            uri = URI.create(url.toString());
            builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(60))
                    .GET();
        } else {
            uri = URI.create(base);
            builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .method(method, HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(inputs)));
        }

        HttpResponse<String> resp = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", resp.statusCode());
        data.put("body", resp.body());
        return data;
    }

    private static int toInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}