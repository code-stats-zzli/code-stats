package com.omni.business.assistance.table.mysql.service;

import com.omni.business.assistance.table.mysql.dto.UserResponse;
import com.omni.business.assistance.table.mysql.dto.UserCreateRequest;
import com.omni.business.assistance.table.mysql.dto.UserUpdateRequest;
import com.omni.business.assistance.table.mysql.dto.User;
import com.omni.business.assistance.table.mysql.mapper.UserMapper;
import com.omni.business.assistance.table.mysql.repository.UserRepository;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.ArrayList;
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
        User user = userMapper.selectUserById(id);
        return user != null ? user.getName() : null;
    }

    public List<String> getAllUserNames() {
        return userMapper.getAllNames();
    }

    // ========== 新增 CRUD 方法 ==========

    public UserResponse getUserById(Integer id) {
        User user = userMapper.selectUserById(id);
        if (user == null) {
            return null;
        }
        return new UserResponse(user.getId(), user.getName(), user.getAge());
    }

    public List<UserResponse> listAllUsers() {
        // ★ 修正：正确接收 List<User>，而不是单个 User
        List<User> users = userMapper.getAllUsers();
        List<UserResponse> responses = new ArrayList<>();
        for (User user : users) {
            responses.add(new UserResponse(user.getId(), user.getName(), user.getAge()));
        }
        return responses;
    }

    public boolean createUser(UserCreateRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setAge(request.getAge());
        int rows = userMapper.insertUser(user);
        return rows > 0;
    }

    public boolean updateUser(UserUpdateRequest request) {
        User user = new User();
        user.setId(request.getId());
        user.setName(request.getName());
        user.setAge(request.getAge());
        int rows = userMapper.updateUser(user);
        return rows > 0;
    }

    public boolean deleteUser(Integer id) {
        int rows = userMapper.deleteUser(id);
        return rows > 0;
    }
}