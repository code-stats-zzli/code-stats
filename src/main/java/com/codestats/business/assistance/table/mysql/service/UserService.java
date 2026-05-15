package com.codestats.business.assistance.table.mysql.service;

import com.codestats.business.assistance.table.mysql.mapper.UserMapper;
import com.codestats.business.assistance.table.mysql.repository.UserRepository;
import com.codestats.framework.spring.annotation.Autowired;
import com.codestats.framework.spring.annotation.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    public String getUserInfo() {
        return "User Info: " + userRepository.findUser();
    }

    public String getUserNameById(Integer id) {
        return userMapper.selectUserNameById(id);
    }

    public List<String> getAllUserNames() {
        return userMapper.selectAllUserNames();
    }
}