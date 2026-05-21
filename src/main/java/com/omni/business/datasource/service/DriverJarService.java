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