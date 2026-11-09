package com.omni.business.assistance.apipublisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.business.assistance.apipublisher.model.ApiDefinition;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class ApiPublishService {
    private static final Logger log = LoggerFactory.getLogger(ApiPublishService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path apiDir;

    public ApiPublishService() {
        this.apiDir = Paths.get(System.getProperty("user.dir"), "resources", "api")
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(apiDir);
            log.info("API 资源目录: {}", apiDir);
        } catch (Exception e) {
            log.error("创建 API 资源目录失败", e);
        }
    }

    public Path getApiDir() { return apiDir; }

    public String save(ApiDefinition def) throws Exception {
        if (def == null) throw new IllegalArgumentException("API 定义为空");
        if (def.getId() == null || def.getId().trim().isEmpty()) {
            def.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        if (def.getCreateTime() == null || def.getCreateTime().isEmpty()) {
            def.setCreateTime(now);
        }
        def.setUpdateTime(now);

        Path file = apiDir.resolve(def.getId() + ".json");
        byte[] data = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(def);
        Files.write(file, data);
        log.info("API 已发布: id={}, name={}, path={}", def.getId(), def.getName(), def.getPath());
        return def.getId();
    }

    public ApiDefinition getById(String id) throws Exception {
        if (id == null || id.trim().isEmpty()) return null;
        Path file = apiDir.resolve(id + ".json");
        if (!Files.isRegularFile(file)) return null;
        return MAPPER.readValue(file.toFile(), ApiDefinition.class);
    }

    public List<ApiDefinition> listAll() throws Exception {
        List<ApiDefinition> list = new ArrayList<>();
        if (!Files.isDirectory(apiDir)) return list;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(apiDir, "*.json")) {
            for (Path p : ds) {
                try {
                    list.add(MAPPER.readValue(p.toFile(), ApiDefinition.class));
                } catch (Exception e) {
                    log.warn("跳过损坏的 API 定义: {}", p, e);
                }
            }
        }
        list.sort(Comparator.comparing(
                (ApiDefinition a) -> a.getUpdateTime() == null ? "" : a.getUpdateTime()
        ).reversed());
        return list;
    }

    public boolean delete(String id) throws Exception {
        if (id == null || id.trim().isEmpty()) return false;
        return Files.deleteIfExists(apiDir.resolve(id + ".json"));
    }

    public ApiDefinition toggle(String id, Integer status) throws Exception {
        ApiDefinition def = getById(id);
        if (def == null) return null;
        def.setStatus(status == null ? 1 : status);
        save(def);
        return def;
    }
}