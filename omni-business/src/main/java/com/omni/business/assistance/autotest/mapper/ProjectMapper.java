package com.omni.business.assistance.autotest.mapper;


import com.omni.business.assistance.autotest.model.Project;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface ProjectMapper {
    @Select("SELECT * FROM project WHERE id = #{id}")
    Project selectById(@Param("id") String id);

    @Select("SELECT * FROM project ORDER BY create_time DESC")
    List<Project> selectAll();

    @Insert("INSERT INTO project (id, project_name, project_code, description, status, create_by) " +
            "VALUES (#{id}, #{projectName}, #{projectCode}, #{description}, #{status}, #{createBy})")
    int insert(Project project);

    @Update("UPDATE project SET project_name = #{projectName}, project_code = #{projectCode}, " +
            "description = #{description}, status = #{status} WHERE id = #{id}")
    int update(Project project);

    @Delete("DELETE FROM project WHERE id = #{id}")
    int delete(@Param("id") String id);
}