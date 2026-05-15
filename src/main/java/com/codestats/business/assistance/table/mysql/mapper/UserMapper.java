package com.codestats.business.assistance.table.mysql.mapper;

import com.codestats.framework.mybatis.annotation.Select;
import com.codestats.framework.spring.annotation.Mapper;


import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT name FROM user WHERE id = ?")
    String selectUserNameById(Integer id);

    @Select("SELECT name FROM user")
    List<String> selectAllUserNames();
}