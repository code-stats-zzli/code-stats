package com.omni.business.assistance.table.mysql.mapper;

import com.omni.business.assistance.table.mysql.dto.User;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT name FROM user")
    List<String> getAllNames();
}