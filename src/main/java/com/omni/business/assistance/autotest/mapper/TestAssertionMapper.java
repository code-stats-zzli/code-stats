package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestAssertion;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestAssertionMapper {
    @Select("SELECT * FROM test_assertion WHERE case_id = #{caseId} ORDER BY assert_order")
    List<TestAssertion> selectByCaseId(@Param("caseId") String caseId);

    @Insert("INSERT INTO test_assertion (id, case_id, step_id, assert_order, assert_type, " +
            "locator_type, locator_value, expected_value, expected_value_type, compare_type, " +
            "custom_script, fail_handle, assert_desc) " +
            "VALUES (#{id}, #{caseId}, #{stepId}, #{assertOrder}, #{assertType}, " +
            "#{locatorType}, #{locatorValue}, #{expectedValue}, #{expectedValueType}, #{compareType}, " +
            "#{customScript}, #{failHandle}, #{assertDesc})")
    int insert(TestAssertion assertion);

    @Update("UPDATE test_assertion SET step_id = #{stepId}, assert_order = #{assertOrder}, assert_type = #{assertType}, " +
            "locator_type = #{locatorType}, locator_value = #{locatorValue}, expected_value = #{expectedValue}, " +
            "expected_value_type = #{expectedValueType}, compare_type = #{compareType}, custom_script = #{customScript}, " +
            "fail_handle = #{failHandle}, assert_desc = #{assertDesc} WHERE id = #{id}")
    int update(TestAssertion assertion);

    @Delete("DELETE FROM test_assertion WHERE id = #{id}")
    int delete(@Param("id") String id);

    @Delete("DELETE FROM test_assertion WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);
}