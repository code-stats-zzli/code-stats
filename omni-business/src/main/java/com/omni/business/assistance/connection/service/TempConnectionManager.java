package com.omni.business.assistance.connection.service;

import com.omni.common.util.DriverFileNameParser;
import com.omni.business.common.util.DriverLoader;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.pool.SimpleDataSource;
import com.omni.framework.spring.annotation.Service;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时连接管理器。
 *  - 建立：通过 SimpleDataSource 承载（支持多次 SQL 执行）
 *  - 执行：仅允许查询类语句
 *  - 关闭：销毁连接池
 *
 * 驱动加载顺序（classpath 放最后兜底）：
 *   ① 用户显式指定 driverClass 且 classpath 上存在 → 直接用
 *   ② 否则统一走 DriverLoader：driver_jar 表 → resources/drivers 目录 → classpath
 */
@Service
public class TempConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(TempConnectionManager.class);

    private static final Map<String, TempConn> REGISTRY = new ConcurrentHashMap<>();

    public static class TempConn {
        public final String id;
        public final String url;
        public final String username;
        public final String password;      // 仅内存持有，供保存为正式数据源时使用
        public final String dbType;
        public final String driverClass;
        public final String version;
        public final SimpleDataSource ds;
        public final long createTime;

        public TempConn(String id, String url, String username, String password,
                        String dbType, String driverClass, String version, SimpleDataSource ds) {
            this.id = id;
            this.url = url;
            this.username = username;
            this.password = password;
            this.dbType = dbType;
            this.driverClass = driverClass;
            this.version = version;
            this.ds = ds;
            this.createTime = System.currentTimeMillis();
        }
    }

    public TempConn open(String url, String username, String password,
                         String dbType, String driverClass, String version) throws Exception {
        if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("连接串不能为空");
        String type = (dbType == null || dbType.trim().isEmpty())
                ? detectType(url)
                : DriverFileNameParser.normalizeDbType(dbType);
        if (!DriverFileNameParser.isAllowedDbType(type)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType
                    + "（仅支持 " + String.join("/", DriverFileNameParser.ALLOWED_DB_TYPES) + "）");
        }

        ensureDriver(type, driverClass, version);

        String validationQuery = "oracle".equalsIgnoreCase(type) ? "SELECT 1 FROM DUAL" : "SELECT 1";
        SimpleDataSource ds = new SimpleDataSource.Builder()
                .url(url.trim())
                .username(username)
                .password(password)
                .maxActive(3)
                .maxIdle(2)
                .minIdle(0)
                .maxWaitMillis(10000)
                .validationQuery(validationQuery)
                .build();

        // 立即验证一次
        try (Connection c = ds.getConnection()) {
            if (!c.isValid(5)) throw new SQLException("连接无效");
        } catch (Exception e) {
            try { ds.close(); } catch (Exception ignored) {}
            throw e;
        }

        String id = "tmp_" + UUID.randomUUID().toString().replace("-", "");
        TempConn tc = new TempConn(id, url.trim(), username, password, type, driverClass, version, ds);
        REGISTRY.put(id, tc);
        log.info("临时连接已建立: id={}, type={}, url={}", id, type, maskUrl(url));
        return tc;
    }

    private String detectType(String url) {
        String u = url.toLowerCase();
        if (u.startsWith("jdbc:mysql:")) return "mysql";
        if (u.startsWith("jdbc:oracle:")) return "oracle";
        if (u.startsWith("jdbc:postgresql:")) return "postgresql";
        if (u.startsWith("jdbc:sqlserver:")) return "sqlserver";
        throw new IllegalArgumentException("无法识别的数据库类型，请指定 dbType");
    }

    /**
     * 驱动加载（classpath 放最后兜底）：
     *   ① 用户显式指定了 driverClass 且 classpath 上存在 → 直接用
     *   ② 否则统一走 DriverLoader：driver_jar 表 → resources/drivers 目录 → classpath
     */
    private void ensureDriver(String dbType, String driverClass, String version) throws Exception {
        // ① 用户显式指定 driverClass → 优先使用（业务层显式要求，视为最高优先级）
        if (driverClass != null && !driverClass.trim().isEmpty()) {
            String cls = driverClass.trim();
            try {
                Class.forName(cls);
                log.info("使用用户显式指定的驱动类: {}", cls);
                return;
            } catch (ClassNotFoundException e) {
                log.warn("用户指定的驱动类 {} 不在 classpath，将回退到 DriverLoader 兜底", cls);
                // fallthrough 到 ②
            }
        }

        // ② 统一交给 DriverLoader：driver_jar 表 → resources/drivers 目录 → classpath（最后兜底）
        Driver d = DriverLoader.getDriver(dbType, version);
        DriverManager.registerDriver(new DelegatingDriver(d));
        log.info("通过 DriverLoader 加载驱动成功: type={}, version={}, class={}",
                dbType, version, d.getClass().getName());
    }

    public TempConn get(String id) { return REGISTRY.get(id); }

    public Map<String, Object> execute(String id, String sql) throws Exception {
        TempConn tc = REGISTRY.get(id);
        if (tc == null) throw new IllegalArgumentException("临时连接不存在或已关闭，请重新连接");

        String trimmed = sql == null ? "" : sql.trim().toLowerCase();
        if (!(trimmed.startsWith("select") || trimmed.startsWith("show") || trimmed.startsWith("desc")
                || trimmed.startsWith("describe") || trimmed.startsWith("explain") || trimmed.startsWith("with"))) {
            throw new SecurityException("仅允许查询类语句（SELECT / SHOW / DESC / EXPLAIN / WITH）");
        }

        long start = System.currentTimeMillis();
        try (Connection conn = tc.ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            List<String> cols = new ArrayList<>();
            for (int i = 1; i <= n; i++) cols.add(md.getColumnLabel(i));

            List<Map<String, Object>> rows = new ArrayList<>();
            int limit = 1000;
            while (rs.next() && rows.size() < limit) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) {
                    Object v = rs.getObject(i);
                    if (v instanceof Clob) {
                        Clob clob = (Clob) v;
                        long len = Math.min(clob.length(), 10000);
                        v = clob.getSubString(1, (int) len);
                    } else if (v instanceof Blob) {
                        v = "BLOB(" + ((Blob) v).length() + " bytes)";
                    }
                    row.put(md.getColumnLabel(i), v);
                }
                rows.add(row);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("columns", cols);
            r.put("rows", rows);
            r.put("total", rows.size());
            r.put("durationMs", System.currentTimeMillis() - start);
            return r;
        }
    }

    public void close(String id) {
        TempConn tc = REGISTRY.remove(id);
        if (tc != null && tc.ds != null) {
            try { tc.ds.close(); } catch (Exception e) {
                log.warn("关闭临时连接池失败: {}", id, e);
            }
            log.info("临时连接已销毁: id={}, type={}", id, tc.dbType);
        }
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        int idx = url.indexOf("://");
        if (idx > 0 && url.length() > idx + 8) return url.substring(0, idx + 3) + "***";
        return url;
    }

    private static class DelegatingDriver implements Driver {
        private final Driver d;
        DelegatingDriver(Driver d) { this.d = d; }
        @Override public Connection connect(String url, Properties info) throws SQLException { return d.connect(url, info); }
        @Override public boolean acceptsURL(String url) throws SQLException { return d.acceptsURL(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException { return d.getPropertyInfo(url, info); }
        @Override public int getMajorVersion() { return d.getMajorVersion(); }
        @Override public int getMinorVersion() { return d.getMinorVersion(); }
        @Override public boolean jdbcCompliant() { return d.jdbcCompliant(); }
        @Override public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException { return d.getParentLogger(); }
    }
}