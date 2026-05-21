package com.omni.business.datasource.service;

import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.spring.annotation.Service;
import java.util.List;
import java.util.UUID;

@Service
public class DriverJarService {
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    public List<DriverJar> listAll() throws Exception {
        return jdbcTemplate.queryForList("SELECT id, name, file_name, create_time FROM driver_jar ORDER BY create_time DESC", DriverJar.class);
    }

    public DriverJar getById(String id) throws Exception {
        return jdbcTemplate.queryForObject("SELECT * FROM driver_jar WHERE id = ?", DriverJar.class, id);
    }

    // 新增：更新驱动（仅更新名称和可选的文件内容）
    public boolean update(DriverJar driver) throws Exception {
        if (driver.getJarData() != null && driver.getJarData().length > 0) {
            // 同时更新名称、JAR数据、文件名
            return jdbcTemplate.update(
                    "UPDATE driver_jar SET name = ?, jar_data = ?, file_name = ? WHERE id = ?",
                    driver.getName(), driver.getJarData(), driver.getFileName(), driver.getId()
            ) > 0;
        } else {
            // 仅更新名称，保留原有JAR数据
            return jdbcTemplate.update(
                    "UPDATE driver_jar SET name = ? WHERE id = ?",
                    driver.getName(), driver.getId()
            ) > 0;
        }
    }

    public boolean save(DriverJar driver) throws Exception {
        driver.setId(UUID.randomUUID().toString().replace("-", ""));
        return jdbcTemplate.update(
                "INSERT INTO driver_jar (id, name, jar_data, file_name) VALUES (?,?,?,?)",
                driver.getId(), driver.getName(), driver.getJarData(), driver.getFileName()
        ) > 0;
    }

    public boolean delete(String id) throws Exception {
        return jdbcTemplate.update("DELETE FROM driver_jar WHERE id = ?", id) > 0;
    }
}