package com.omni.business.assistance.autotest.mapper;

import com.omni.business.assistance.autotest.model.TestData;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestDataMapper {
    @Select("SELECT * FROM test_data WHERE case_id = #{caseId} AND env_type IN (0, #{envType})")
    List<TestData> selectByCaseIdAndEnv(@Param("caseId") String caseId, @Param("envType") Integer envType);

    @Insert("INSERT INTO test_data (id, case_id, data_name, data_key, data_value, data_type, env_type, description) " +
            "VALUES (#{id}, #{caseId}, #{dataName}, #{dataKey}, #{dataValue}, #{dataType}, #{envType}, #{description})")
    int insert(TestData data);
}