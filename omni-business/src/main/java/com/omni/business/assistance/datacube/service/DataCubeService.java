package com.omni.business.assistance.datacube.service;

import com.omni.framework.datacube.CubeEngine;
import com.omni.framework.datacube.model.CubeQueryResult;
import com.omni.framework.datacube.model.DataCube;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据立方业务服务：CRUD（内存存储，Phase 2 可换 Mapper）
 */
@Service
public class DataCubeService {

    private final Map<String, DataCube> STORE = new ConcurrentHashMap<>();

    @Autowired
    private CubeEngine cubeEngine;

    public List<DataCube> list() {
        return STORE.values().stream()
                .sorted(Comparator.comparing(DataCube::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public DataCube getById(String id) {
        return STORE.get(id);
    }

    public DataCube save(DataCube cube) {
        if (cube.getId() == null || cube.getId().isEmpty()) {
            cube.setId("cube_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            cube.setCreatedAt(System.currentTimeMillis());
        }
        cube.setUpdatedAt(System.currentTimeMillis());
        STORE.put(cube.getId(), cube);
        return cube;
    }

    public boolean delete(String id) {
        return STORE.remove(id) != null;
    }

    /** 执行查询（有 id 时走缓存/完整流程） */
    public CubeQueryResult query(String cubeId, Map<String, Object> params) {
        DataCube cube = STORE.get(cubeId);
        if (cube == null) return CubeQueryResult.error("cube 不存在: " + cubeId);
        return cubeEngine.execute(cube, params);
    }

    /** 临时预览：直接传 type + config，不落库 */
    public CubeQueryResult preview(DataCube tempCube, Map<String, Object> params) {
        return cubeEngine.execute(tempCube, params);
    }
}