package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestStep;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestStepMapper extends BaseMapper<TestStep> {

    @Select("SELECT * FROM test_step WHERE case_id = #{caseId} ORDER BY step_order")
    List<TestStep> selectByCaseId(@Param("caseId") String caseId);

    @Delete("DELETE FROM test_step WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);

    default int delete(String id) { return deleteById(id); }
}