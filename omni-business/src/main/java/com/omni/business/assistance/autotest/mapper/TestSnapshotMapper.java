package com.omni.business.assistance.autotest.mapper;


import com.omni.business.assistance.autotest.model.TestSnapshot;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestSnapshotMapper {
    @Select("SELECT * FROM test_snapshot WHERE execution_id = #{executionId} ORDER BY step_order")
    List<TestSnapshot> selectByExecutionId(@Param("executionId") String executionId);

    @Insert("INSERT INTO test_snapshot (id, execution_id, step_id, step_order, action_type, step_desc, " +
            "html_content, html_size, screenshot_url, screenshot_thumbnail_url, element_tag, element_selector, " +
            "element_rect, element_text, is_success, error_msg, error_stack, exec_time_ms, " +
            "variables_snapshot, cookies_snapshot) " +
            "VALUES (#{id}, #{executionId}, #{stepId}, #{stepOrder}, #{actionType}, #{stepDesc}, " +
            "#{htmlContent}, #{htmlSize}, #{screenshotUrl}, #{screenshotThumbnailUrl}, #{elementTag}, #{elementSelector}, " +
            "#{elementRect}, #{elementText}, #{isSuccess}, #{errorMsg}, #{errorStack}, #{execTimeMs}, " +
            "#{variablesSnapshot}, #{cookiesSnapshot})")
    int insert(TestSnapshot snapshot);
}