package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestAssertion;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestAssertionMapper extends BaseMapper<TestAssertion> {

    @Select("SELECT * FROM test_assertion WHERE case_id = #{caseId} ORDER BY assert_order")
    List<TestAssertion> selectByCaseId(@Param("caseId") String caseId);

    @Delete("DELETE FROM test_assertion WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);

    default int delete(String id) { return deleteById(id); }
}