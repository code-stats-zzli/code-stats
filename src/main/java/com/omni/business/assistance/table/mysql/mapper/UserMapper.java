package com.omni.business.assistance.table.mysql.mapper;

import com.omni.business.assistance.table.mysql.dto.User;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.spring.annotation.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectUserById(@Param("id") Integer id);

    @Insert("INSERT INTO user(name, age) VALUES(#{name}, #{age})")
    int insertUser(User user);

    @Update("UPDATE user SET name = #{name}, age = #{age} WHERE id = #{id}")
    int updateUser(User user);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteUser(@Param("id") Integer id);

    @Select("SELECT name FROM user")
    List<String> getAllNames();

    @Select("SELECT id, name, age FROM user")
    List<User> getAllUsers();
}