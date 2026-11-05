package com.omni.framework.datacube.spi;

import com.omni.framework.datacube.model.CubeQueryResult;
import com.omni.framework.datacube.model.CubeSchema;
import com.omni.framework.datacube.model.CubeType;
import com.omni.framework.datacube.model.DataCube;

import java.util.Map;

/**
 * 数据立方 Provider SPI — 每种数据来源实现此接口。
 *   getType()  返回唯一类型标识
 *   query()    执行查询，输出统一结果
 *   describe() 探测元数据（列定义）
 */
public interface CubeProvider {
    CubeType getType();

    CubeQueryResult query(DataCube cube, Map<String, Object> params) throws Exception;

    CubeSchema describe(DataCube cube) throws Exception;
}