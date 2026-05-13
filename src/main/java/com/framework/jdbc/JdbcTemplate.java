package com.framework.jdbc;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {

    /**
     * 执行查询，支持超时设置
     * @param sql SQL语句
     * @param timeoutSeconds 超时秒数，<=0 则不设置超时
     * @param rowMapper 行映射器
     * @param args 参数
     * @return 结果列表
     */
    public <T> List<T> query(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) {
        List<T> results = new ArrayList<>();
        try (Connection conn = DataSourceUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // 设置超时
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
            throw new RuntimeException("Query failed: " + sql, e);
        }
        return results;
    }

    /**
     * 查询单条记录，支持超时
     */
    public <T> T queryOne(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) {
        List<T> list = query(sql, timeoutSeconds, rowMapper, args);
        return list.isEmpty() ? null : list.get(0);
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet rs) throws SQLException;
    }
}