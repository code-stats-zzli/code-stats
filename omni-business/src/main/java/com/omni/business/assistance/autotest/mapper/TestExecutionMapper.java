package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestExecution;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestExecutionMapper extends BaseMapper<TestExecution> {

    @Select("SELECT * FROM test_execution WHERE case_id = #{caseId} ORDER BY create_time DESC")
    List<TestExecution> selectByCaseId(@Param("caseId") String caseId);

    default int delete(String id) { return deleteById(id); }
}