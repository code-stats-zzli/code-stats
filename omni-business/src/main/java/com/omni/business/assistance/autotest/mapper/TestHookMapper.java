package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestHook;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestHookMapper extends BaseMapper<TestHook> {

    @Select("SELECT * FROM test_hook WHERE case_id = #{caseId} AND enabled = 1 ORDER BY hook_order")
    List<TestHook> selectByCaseId(@Param("caseId") String caseId);

    @Delete("DELETE FROM test_hook WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);
}