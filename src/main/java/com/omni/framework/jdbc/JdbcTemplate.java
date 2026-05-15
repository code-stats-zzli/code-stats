package com.omni.framework.jdbc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {
    private static final Logger log = LoggerFactory.getLogger(JdbcTemplate.class);

    public <T> List<T> query(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) {
        List<T> results = new ArrayList<>();
        try (Connection conn = DataSourceUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (timeoutSeconds > 0) {
                ps.setQueryTimeout(timeoutSeconds);
            }
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Query failed: {}", sql, e);
            throw new RuntimeException("Query failed: " + sql, e);
        }
        return results;
    }

    public <T> T queryOne(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) {
        List<T> list = query(sql, timeoutSeconds, rowMapper, args);
        return list.isEmpty() ? null : list.get(0);
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet rs) throws SQLException;
    }
}