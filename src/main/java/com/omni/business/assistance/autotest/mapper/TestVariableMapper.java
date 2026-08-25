package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestVariable;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestVariableMapper {
    @Select("SELECT * FROM test_variable WHERE case_id = #{caseId}")
    List<TestVariable> selectByCaseId(@Param("caseId") String caseId);

    @Insert("INSERT INTO test_variable (id, case_id, var_name, var_scope, extract_type, locator_type, " +
            "locator_value, extract_expression, default_value, is_required) " +
            "VALUES (#{id}, #{caseId}, #{varName}, #{varScope}, #{extractType}, #{locatorType}, " +
            "#{locatorValue}, #{extractExpression}, #{defaultValue}, #{isRequired})")
    int insert(TestVariable variable);

    @Delete("DELETE FROM test_variable WHERE case_id = #{caseId}")
    int deleteByCaseId(@Param("caseId") String caseId);
}