package com.omni.business.assistance.autotest.mapper;


import com.omni.business.assistance.autotest.model.TestExecution;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestExecutionMapper {
    @Select("SELECT * FROM test_execution WHERE id = #{id}")
    TestExecution selectById(@Param("id") String id);

    @Select("SELECT * FROM test_execution WHERE case_id = #{caseId} ORDER BY create_time DESC")
    List<TestExecution> selectByCaseId(@Param("caseId") String caseId);

    @Insert("INSERT INTO test_execution (id, case_id, execution_code, trigger_type, trigger_user, trigger_source, " +
            "env_type, browser_type, browser_version, os_type, os_version, start_time, end_time, " +
            "total_steps, pass_count, fail_count, skip_count, pass_rate, status, error_message, " +
            "total_time_ms, avg_step_time_ms, max_step_time_ms, report_url, report_json_url, video_url, log_url) " +
            "VALUES (#{id}, #{caseId}, #{executionCode}, #{triggerType}, #{triggerUser}, #{triggerSource}, " +
            "#{envType}, #{browserType}, #{browserVersion}, #{osType}, #{osVersion}, #{startTime}, #{endTime}, " +
            "#{totalSteps}, #{passCount}, #{failCount}, #{skipCount}, #{passRate}, #{status}, #{errorMessage}, " +
            "#{totalTimeMs}, #{avgStepTimeMs}, #{maxStepTimeMs}, #{reportUrl}, #{reportJsonUrl}, #{videoUrl}, #{logUrl})")
    int insert(TestExecution execution);

    @Update("UPDATE test_execution SET status = #{status}, pass_count = #{passCount}, fail_count = #{failCount}, " +
            "pass_rate = #{passRate}, end_time = #{endTime}, total_time_ms = #{totalTimeMs} WHERE id = #{id}")
    int update(TestExecution execution);

    @Delete("DELETE FROM test_execution WHERE id = #{id}")
    int delete(@Param("id") String id);
}