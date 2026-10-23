package com.omni.business.datasource.service;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DriverJarService {
    private static final Logger log = LoggerFactory.getLogger(DriverJarService.class);
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    public List<DriverJar> listAll() throws Exception {
        return jdbcTemplate.queryForList(
                "SELECT id, name, file_name, create_time FROM driver_jar ORDER BY create_time DESC",
                DriverJar.class);
    }

    public DriverJar getById(String id) throws Exception {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM driver_jar WHERE id = ?", DriverJar.class, id);
    }

    /**
     * ★ 修复：
     * 1) INSERT 显式写入 create_time，避免 NOT NULL 且无默认值时失败
     * 2) catch RuntimeException 后把底层 SQLException 的 message 拼进新异常
     */
    public boolean save(DriverJar driver) throws Exception {
        driver.setId(UUID.randomUUID().toString().replace("-", ""));
        try {
            int rows = jdbcTemplate.update(
                    "INSERT INTO driver_jar (id, name, jar_data, file_name, create_time) " +
                            "VALUES (?,?,?,?, NOW())",
                    driver.getId(), driver.getName(), driver.getJarData(), driver.getFileName());
            log.info("驱动插入成功: id={}, name={}, size={} bytes",
                    driver.getId(), driver.getName(),
                    driver.getJarData() == null ? 0 : driver.getJarData().length);
            return rows > 0;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null
                    ? cause.getClass().getSimpleName() + ": " + cause.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("驱动插入失败: {}", detail, e);
            throw new Exception("保存驱动失败: " + detail, e);
        }
    }

    public boolean update(DriverJar driver) throws Exception {
        try {
            boolean result;
            if (driver.getJarData() != null && driver.getJarData().length > 0) {
                result = jdbcTemplate.update(
                        "UPDATE driver_jar SET name = ?, jar_data = ?, file_name = ? WHERE id = ?",
                        driver.getName(), driver.getJarData(), driver.getFileName(), driver.getId()
                ) > 0;
            } else {
                result = jdbcTemplate.update(
                        "UPDATE driver_jar SET name = ? WHERE id = ?",
                        driver.getName(), driver.getId()
                ) > 0;
            }
            log.info("驱动更新{}: id={}, name={}", result ? "成功" : "无变化",
                    driver.getId(), driver.getName());
            return result;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null
                    ? cause.getClass().getSimpleName() + ": " + cause.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("驱动更新失败: {}", detail, e);
            throw new Exception("更新驱动失败: " + detail, e);
        }
    }

    public boolean delete(String id) throws Exception {
        try {
            return jdbcTemplate.update("DELETE FROM driver_jar WHERE id = ?", id) > 0;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            log.error("驱动删除失败: id={}, {}", id, detail, e);
            throw new Exception("删除驱动失败: " + detail, e);
        }
    }
}