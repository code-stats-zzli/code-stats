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
                "SELECT id, name, db_type, version, file_name, create_time FROM driver_jar ORDER BY create_time DESC",
                DriverJar.class);
    }

    /** ★ 按数据库类型过滤 */
    public List<DriverJar> listByDbType(String dbType) throws Exception {
        if (dbType == null || dbType.trim().isEmpty()) return listAll();
        return jdbcTemplate.queryForList(
                "SELECT id, name, db_type, version, file_name, create_time FROM driver_jar " +
                        "WHERE LOWER(db_type) = LOWER(?) ORDER BY create_time DESC",
                DriverJar.class, dbType.trim());
    }

    public DriverJar getById(String id) throws Exception {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM driver_jar WHERE id = ?", DriverJar.class, id);
    }

    /** ★ 唯一性校验：db_type + name + version 唯一 */
    public String findExistingId(String dbType, String name, String version) throws Exception {
        String sql = "SELECT id FROM driver_jar WHERE LOWER(db_type)=LOWER(?) " +
                "AND LOWER(name)=LOWER(?) AND LOWER(version)=LOWER(?) LIMIT 1";
        List<DriverJar> list = jdbcTemplate.queryForList(sql, DriverJar.class,
                dbType == null ? "" : dbType.trim(),
                name == null ? "" : name.trim(),
                version == null ? "" : version.trim());
        return list.isEmpty() ? null : list.get(0).getId();
    }

    public boolean save(DriverJar driver) throws Exception {
        if (driver.getDbType() == null || driver.getDbType().trim().isEmpty())
            throw new IllegalArgumentException("数据库类型 dbType 不能为空");
        if (driver.getName() == null || driver.getName().trim().isEmpty())
            throw new IllegalArgumentException("驱动名称 name 不能为空");
        if (driver.getVersion() == null || driver.getVersion().trim().isEmpty())
            throw new IllegalArgumentException("驱动版本 version 不能为空");

        String existId = findExistingId(driver.getDbType(), driver.getName(), driver.getVersion());
        if (existId != null) {
            throw new IllegalStateException("驱动已存在（" + driver.getDbType() + " / "
                    + driver.getName() + " / " + driver.getVersion() + "），不可重复上传");
        }

        driver.setId(UUID.randomUUID().toString().replace("-", ""));
        try {
            int rows = jdbcTemplate.update(
                    "INSERT INTO driver_jar (id, name, db_type, version, jar_data, file_name, create_time) " +
                            "VALUES (?,?,?,?,?,?, NOW())",
                    driver.getId(),
                    driver.getName().trim(),
                    driver.getDbType().trim().toLowerCase(),
                    driver.getVersion().trim(),
                    driver.getJarData(),
                    driver.getFileName());
            log.info("驱动插入成功: id={}, dbType={}, name={}, version={}, size={} bytes",
                    driver.getId(), driver.getDbType(), driver.getName(), driver.getVersion(),
                    driver.getJarData() == null ? 0 : driver.getJarData().length);
            return rows > 0;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            if (detail != null && detail.toLowerCase().contains("duplicate")) {
                throw new IllegalStateException("驱动已存在（唯一索引冲突），不可重复上传", e);
            }
            log.error("驱动插入失败: {}", detail, e);
            throw new Exception("保存驱动失败: " + detail, e);
        }
    }

    public boolean update(DriverJar driver) throws Exception {
        try {
            boolean result;
            if (driver.getJarData() != null && driver.getJarData().length > 0) {
                result = jdbcTemplate.update(
                        "UPDATE driver_jar SET name = ?, db_type = ?, version = ?, " +
                                "jar_data = ?, file_name = ? WHERE id = ?",
                        driver.getName(), driver.getDbType(), driver.getVersion(),
                        driver.getJarData(), driver.getFileName(), driver.getId()
                ) > 0;
            } else {
                result = jdbcTemplate.update(
                        "UPDATE driver_jar SET name = ?, db_type = ?, version = ? WHERE id = ?",
                        driver.getName(), driver.getDbType(), driver.getVersion(), driver.getId()
                ) > 0;
            }
            log.info("驱动更新{}: id={}, dbType={}, name={}, version={}",
                    result ? "成功" : "无变化",
                    driver.getId(), driver.getDbType(), driver.getName(), driver.getVersion());
            return result;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
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