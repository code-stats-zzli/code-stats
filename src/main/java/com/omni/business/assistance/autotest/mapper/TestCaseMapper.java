package com.omni.business.assistance.autotest.mapper;



import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestCaseMapper {

    @Select("SELECT * FROM test_case WHERE id = #{id}")
    com.omni.autotest.core.model.TestCase selectById(@Param("id") String id);

    @Select("SELECT * FROM test_case WHERE project_id = #{projectId} AND status = 1 ORDER BY create_time DESC")
    List<com.omni.autotest.core.model.TestCase> selectByProjectId(@Param("projectId") String projectId);

    @Insert("INSERT INTO test_case (id, project_id, case_name, case_code, module_path, priority, env_type, " +
            "browser_type, base_url, timeout_sec, retry_times, status, tags, create_by) " +
            "VALUES (#{id}, #{projectId}, #{caseName}, #{caseCode}, #{modulePath}, #{priority}, #{envType}, " +
            "#{browserType}, #{baseUrl}, #{timeoutSec}, #{retryTimes}, #{status}, #{tags}, #{createBy})")
    int insert(com.omni.autotest.core.model.TestCase testCase);

    @Update("UPDATE test_case SET case_name = #{caseName}, module_path = #{modulePath}, priority = #{priority}, " +
            "env_type = #{envType}, browser_type = #{browserType}, base_url = #{baseUrl}, timeout_sec = #{timeoutSec}, " +
            "retry_times = #{retryTimes}, status = #{status}, tags = #{tags} WHERE id = #{id}")
    int update(com.omni.autotest.core.model.TestCase testCase);

    @Delete("DELETE FROM test_case WHERE id = #{id}")
    int delete(@Param("id") String id);
}