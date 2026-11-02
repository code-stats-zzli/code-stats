package com.omni.business.datasource.service;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.common.util.DriverResourceScanner;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 驱动 jar 服务。
 *
 * <p>唯一性约束：{@code db_type + version}（name 恒等于 db_type）</p>
 */
@Service
public class DriverJarService {
    private static final Logger log = LoggerFactory.getLogger(DriverJarService.class);
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    // ==================== 查询 ====================

    public List<DriverJar> listAll() throws Exception {
        return jdbcTemplate.queryForList(
                "SELECT id, name, db_type, version, file_name, create_time FROM driver_jar " +
                        "ORDER BY db_type ASC, create_time DESC", DriverJar.class);
    }

    public List<DriverJar> listByDbType(String dbType) throws Exception {
        if (dbType == null || dbType.trim().isEmpty()) return listAll();
        return jdbcTemplate.queryForList(
                "SELECT id, name, db_type, version, file_name, create_time FROM driver_jar " +
                        "WHERE LOWER(db_type) = LOWER(?) ORDER BY create_time DESC",
                DriverJar.class, DriverResourceScanner.normalizeDbType(dbType));
    }

    public DriverJar getById(String id) throws Exception {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM driver_jar WHERE id = ?", DriverJar.class, id);
    }

    /** 唯一性判断：db_type + version */
    public String findExistingId(String dbType, String version) throws Exception {
        String sql = "SELECT id FROM driver_jar " +
                "WHERE LOWER(db_type)=LOWER(?) AND LOWER(version)=LOWER(?) LIMIT 1";
        List<DriverJar> list = jdbcTemplate.queryForList(sql, DriverJar.class,
                dbType == null ? "" : dbType.trim(),
                version == null ? "" : version.trim());
        return list.isEmpty() ? null : list.get(0).getId();
    }

    // ==================== 保存（上传 → 入库 + 落盘） ====================

    public boolean save(DriverJar driver) throws Exception {
        String dbType = DriverResourceScanner.normalizeDbType(driver.getDbType());
        if (!DriverResourceScanner.isAllowedDbType(dbType)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + driver.getDbType()
                    + "（仅支持 " + String.join("/", DriverResourceScanner.ALLOWED_DB_TYPES) + "）");
        }
        if (driver.getVersion() == null || driver.getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("驱动版本 version 不能为空");
        }
        if (driver.getJarData() == null || driver.getJarData().length == 0) {
            throw new IllegalArgumentException("驱动 jar 文件不能为空");
        }
        if (driver.getFileName() == null || driver.getFileName().trim().isEmpty()) {
            throw new IllegalArgumentException("驱动原始文件名不能为空");
        }

        // 唯一性校验：db_type + version
        String existId = findExistingId(dbType, driver.getVersion());
        if (existId != null) {
            throw new IllegalStateException("驱动已存在（dbType=" + dbType
                    + " / version=" + driver.getVersion() + "），不可重复上传");
        }

        // name = dbType（表结构兼容老数据）
        driver.setName(dbType);
        driver.setDbType(dbType);
        driver.setVersion(driver.getVersion().trim());
        driver.setId(UUID.randomUUID().toString().replace("-", ""));

        int rows;
        try {
            rows = jdbcTemplate.update(
                    "INSERT INTO driver_jar (id, name, db_type, version, jar_data, file_name, create_time) " +
                            "VALUES (?,?,?,?,?,?, NOW())",
                    driver.getId(),
                    driver.getName(),
                    driver.getDbType(),
                    driver.getVersion(),
                    driver.getJarData(),
                    driver.getFileName());
            log.info("驱动入库成功: id={}, dbType={}, version={}, file={}, size={}B",
                    driver.getId(), driver.getDbType(), driver.getVersion(),
                    driver.getFileName(), driver.getJarData().length);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            if (detail != null && detail.toLowerCase().contains("duplicate")) {
                throw new IllegalStateException("驱动已存在（唯一索引冲突），不可重复上传", e);
            }
            log.error("驱动插入失败: {}", detail, e);
            throw new Exception("保存驱动失败: " + detail, e);
        }

        // 同步落盘到 resources/drivers
        try {
            Path saved = DriverResourceScanner.writeJar(
                    driver.getDbType(), driver.getVersion(),
                    driver.getFileName(), driver.getJarData());
            log.info("驱动已同步到资源目录: {}", saved);
        } catch (Exception e) {
            log.warn("驱动已入库，但同步到资源目录失败: id={}, reason={}",
                    driver.getId(), e.getMessage(), e);
        }

        return rows > 0;
    }

    // ==================== 更新 ====================

    public boolean update(DriverJar driver) throws Exception {
        String dbType = DriverResourceScanner.normalizeDbType(driver.getDbType());
        if (!DriverResourceScanner.isAllowedDbType(dbType)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + driver.getDbType());
        }
        String version = driver.getVersion() == null ? null : driver.getVersion().trim();
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("驱动版本 version 不能为空");
        }
        // name 恒等于 dbType
        driver.setName(dbType);
        driver.setDbType(dbType);
        driver.setVersion(version);

        try {
            boolean result;
            if (driver.getJarData() != null && driver.getJarData().length > 0) {
                result = jdbcTemplate.update(
                        "UPDATE driver_jar SET name=?, db_type=?, version=?, jar_data=?, file_name=? WHERE id=?",
                        driver.getName(), driver.getDbType(), driver.getVersion(),
                        driver.getJarData(), driver.getFileName(), driver.getId()) > 0;
            } else {
                result = jdbcTemplate.update(
                        "UPDATE driver_jar SET name=?, db_type=?, version=? WHERE id=?",
                        driver.getName(), driver.getDbType(), driver.getVersion(),
                        driver.getId()) > 0;
            }

            // 更新时也同步刷新资源目录里的文件
            if (result && driver.getJarData() != null && driver.getJarData().length > 0) {
                try {
                    DriverResourceScanner.writeJar(
                            driver.getDbType(), driver.getVersion(),
                            driver.getFileName(), driver.getJarData());
                } catch (Exception e) {
                    log.warn("更新驱动后落盘失败: id={}", driver.getId(), e);
                }
            }
            return result;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            log.error("驱动更新失败: {}", detail, e);
            throw new Exception("更新驱动失败: " + detail, e);
        }
    }

    // ==================== 删除 ====================

    public boolean delete(String id) throws Exception {
        DriverJar d = getById(id);   // 先查出以定位磁盘文件
        try {
            boolean ok = jdbcTemplate.update("DELETE FROM driver_jar WHERE id = ?", id) > 0;
            if (ok && d != null && d.getVersion() != null && d.getFileName() != null) {
                try {
                    String storageName = DriverResourceScanner.buildStorageName(
                            d.getDbType(), d.getVersion(), d.getFileName());
                    Path target = DriverResourceScanner.getDriversDir().resolve(storageName);
                    if (Files.deleteIfExists(target)) {
                        log.info("已删除资源目录中的驱动文件: {}", target);
                    }
                } catch (Exception e) {
                    log.warn("删除驱动落盘文件失败: id={}", id, e);
                }
            }
            return ok;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            log.error("驱动删除失败: id={}, {}", id, detail, e);
            throw new Exception("删除驱动失败: " + detail, e);
        }
    }

    // ==================== 资源目录导入 ====================

    /**
     * 把 resources/drivers 目录中的 jar 导入到 driver_jar 表。
     * 文件名前缀即 dbType；已存在的（db_type + version）跳过。
     *
     * @param dbTypeFilter 可选，指定只导入某个 dbType
     */
    public List<DriverJar> importFromResourceDir(String dbTypeFilter) throws Exception {
        String filter = (dbTypeFilter == null || dbTypeFilter.trim().isEmpty())
                ? null : DriverResourceScanner.normalizeDbType(dbTypeFilter);

        List<DriverJar> imported = new ArrayList<>();
        List<DriverResourceScanner.DriverFile> files = DriverResourceScanner.scan();
        for (DriverResourceScanner.DriverFile df : files) {
            if (filter != null && !filter.equalsIgnoreCase(df.dbType)) continue;

            String existId = findExistingId(df.dbType, df.version);
            if (existId != null) {
                log.info("驱动已存在，跳过: {} {}", df.dbType, df.version);
                continue;
            }
            DriverJar dj = new DriverJar();
            dj.setId(UUID.randomUUID().toString().replace("-", ""));
            dj.setName(df.dbType);
            dj.setDbType(df.dbType);
            dj.setVersion(df.version);
            dj.setFileName(df.originalFileName);
            dj.setJarData(Files.readAllBytes(df.jarPath));
            jdbcTemplate.update(
                    "INSERT INTO driver_jar (id, name, db_type, version, jar_data, file_name, create_time) " +
                            "VALUES (?,?,?,?,?,?, NOW())",
                    dj.getId(), dj.getName(), dj.getDbType(), dj.getVersion(),
                    dj.getJarData(), dj.getFileName());
            imported.add(dj);
            log.info("从资源目录导入驱动: dbType={}, version={}", df.dbType, df.version);
        }
        return imported;
    }
}