package com.omni.business.datasource.service;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.common.util.DriverFileNameParser;
import com.omni.business.common.util.DriverResourceScanner;
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
 * <p>唯一性约束：{@code db_type + version}</p>
 * <p>name 恒等于 db_type（表结构兼容历史数据）</p>
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
                DriverJar.class, DriverFileNameParser.normalizeDbType(dbType));
    }

    public DriverJar getById(String id) throws Exception {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM driver_jar WHERE id = ?", DriverJar.class, id);
    }

    public String findExistingId(String dbType, String version) throws Exception {
        List<DriverJar> list = jdbcTemplate.queryForList(
                "SELECT id FROM driver_jar WHERE LOWER(db_type)=LOWER(?) AND LOWER(version)=LOWER(?) LIMIT 1",
                DriverJar.class,
                dbType == null ? "" : dbType.trim(),
                version == null ? "" : version.trim());
        return list.isEmpty() ? null : list.get(0).getId();
    }

    // ==================== 保存（上传 → 入库 + 落盘） ====================

    public boolean save(DriverJar driver) throws Exception {
        // 1. 类型校验
        String dbType = DriverFileNameParser.normalizeDbType(driver.getDbType());
        if (!DriverFileNameParser.isAllowedDbType(dbType)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + driver.getDbType()
                    + "（仅支持 " + String.join("/", DriverFileNameParser.ALLOWED_DB_TYPES) + "）");
        }
        // 2. 版本必填
        if (driver.getVersion() == null || driver.getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("驱动版本 version 不能为空");
        }
        // 3. jar 内容必须存在
        if (driver.getJarData() == null || driver.getJarData().length == 0) {
            throw new IllegalArgumentException("驱动 jar 文件不能为空");
        }
        // 4. 原始文件名必须是 .jar
        String originalFileName = driver.getFileName();
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("驱动原始文件名不能为空");
        }
        if (!originalFileName.trim().toLowerCase().endsWith(".jar")) {
            throw new IllegalArgumentException("只允许上传 .jar 文件");
        }

        // 5. 唯一性校验：db_type + version
        String existId = findExistingId(dbType, driver.getVersion());
        if (existId != null) {
            throw new IllegalStateException("驱动已存在（dbType=" + dbType
                    + " / version=" + driver.getVersion() + "），不可重复上传");
        }

        // 6. 归一化并入库
        driver.setName(dbType);
        driver.setDbType(dbType);
        driver.setVersion(driver.getVersion().trim());
        driver.setFileName(originalFileName.trim());
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

        // 7. 同步落盘到 resources/drivers（失败仅警告）
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
        String dbType = DriverFileNameParser.normalizeDbType(driver.getDbType());
        if (!DriverFileNameParser.isAllowedDbType(dbType)) {
            throw new IllegalArgumentException("不支持的数据库类型: " + driver.getDbType());
        }
        String version = driver.getVersion() == null ? null : driver.getVersion().trim();
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("驱动版本 version 不能为空");
        }
        // 更新 jar 时再次校验后缀
        if (driver.getJarData() != null && driver.getJarData().length > 0) {
            if (driver.getFileName() == null
                    || !driver.getFileName().trim().toLowerCase().endsWith(".jar")) {
                throw new IllegalArgumentException("只允许上传 .jar 文件");
            }
        }
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
        DriverJar d = getById(id);
        try {
            boolean ok = jdbcTemplate.update("DELETE FROM driver_jar WHERE id = ?", id) > 0;
            if (ok && d != null && d.getVersion() != null && d.getFileName() != null) {
                try {
                    String storageName = DriverFileNameParser.buildStorageName(
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

    public List<DriverJar> importFromResourceDir(String dbTypeFilter) throws Exception {
        String filter = (dbTypeFilter == null || dbTypeFilter.trim().isEmpty())
                ? null : DriverFileNameParser.normalizeDbType(dbTypeFilter);

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