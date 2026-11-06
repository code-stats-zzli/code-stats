package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestSnapshot;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestSnapshotMapper extends BaseMapper<TestSnapshot> {

    @Select("SELECT * FROM test_snapshot WHERE execution_id = #{executionId} ORDER BY step_order")
    List<TestSnapshot> selectByExecutionId(@Param("executionId") String executionId);
}