package com.omni.business.assistance.autotest.mapper;


import com.omni.business.assistance.autotest.model.TestStep;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestStepMapper {

    @Select("SELECT * FROM test_step WHERE case_id = #{caseId} ORDER BY step_order")
    List<TestStep> selectByCaseId(@Param("caseId") String caseId);

    @Insert("INSERT INTO test_step (id, case_id, step_order, parent_step_id, step_type, action_type, " +
            "locator_type, locator_value, locator_priority, action_value, action_value_type, wait_type, " +
            "wait_seconds, wait_until_value, fail_handle, ignore_exception, step_desc) " +
            "VALUES (#{id}, #{caseId}, #{stepOrder}, #{parentStepId}, #{stepType}, #{actionType}, " +
            "#{locatorType}, #{locatorValue}, #{locatorPriority}, #{actionValue}, #{actionValueType}, " +
            "#{waitType}, #{waitSeconds}, #{waitUntilValue}, #{failHandle}, #{ignoreException}, #{stepDesc})")
    int insert(TestStep step);

    @Update("UPDATE test_step SET step_order = #{stepOrder}, parent_step_id = #{parentStepId}, step_type = #{stepType}, " +
            "action_type = #{actionType}, locator_type = #{locatorType}, locator_value = #{locatorValue}, " +
            "locator_priority = #{locatorPriority}, action_value = #{actionValue}, action_value_type = #{actionValueType}, " +
            "wait_type = #{waitType}, wait_seconds = #{waitSeconds}, wait_until_value = #{waitUntilValue}, " +
            "fail_handle = #{failHandle}, ignore_exception = #{ignoreException}, step_desc = #{stepDesc} " +
            "WHERE id = #{id}")
    int update(TestStep step);

    @Delete("DELETE FROM test_step WHERE id = #{id}")
    int delete(@Param("id") String id);

    @Delete("DELETE FROM test_step WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);
}