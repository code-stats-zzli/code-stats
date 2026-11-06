package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestVariable;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestVariableMapper extends BaseMapper<TestVariable> {

    @Select("SELECT * FROM test_variable WHERE case_id = #{caseId}")
    List<TestVariable> selectByCaseId(@Param("caseId") String caseId);

    @Delete("DELETE FROM test_variable WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);
}