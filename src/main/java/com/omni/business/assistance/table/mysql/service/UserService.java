package com.omni.business.assistance.table.mysql.service;

import com.omni.business.assistance.table.mysql.mapper.UserMapper;
import com.omni.business.assistance.table.mysql.repository.UserRepository;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

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
        return "";
    }

    public List<String> getAllUserNames() {
        return null;
    }
}