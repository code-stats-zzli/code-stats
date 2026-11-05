package com.omni.business.assistance.autotest.mapper;


import com.omni.business.assistance.autotest.model.TestHook;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestHookMapper {
    @Select("SELECT * FROM test_hook WHERE case_id = #{caseId} AND enabled = 1 ORDER BY hook_order")
    List<TestHook> selectByCaseId(@Param("caseId") String caseId);

    @Insert("INSERT INTO test_hook (id, case_id, hook_type, hook_order, script_type, script_content, script_desc, enabled) " +
            "VALUES (#{id}, #{caseId}, #{hookType}, #{hookOrder}, #{scriptType}, #{scriptContent}, #{scriptDesc}, #{enabled})")
    int insert(TestHook hook);

    @Delete("DELETE FROM test_hook WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);
}