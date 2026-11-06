package com.omni.business.assistance.autotest.mapper;

import com.omni.autotest.core.model.TestCase;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {

    /** 按项目查询（自定义） */
    @Select("SELECT * FROM test_case WHERE project_id = #{projectId} AND status = 1 ORDER BY create_time DESC")
    List<TestCase> selectByProjectId(@Param("projectId") String projectId);

    /** 兼容旧调用：等价于 deleteById */
    default int delete(String id) { return deleteById(id); }
}