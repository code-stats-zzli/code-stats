package com.omni.business.assistance.table.mysql.controller;

import com.omni.business.assistance.table.mysql.service.IUserService;
import com.omni.business.assistance.table.mysql.service.UserService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private IUserService userService0;

    @RequestMapping("/info")
    public String getUserInfo() {
        return userService.getUserInfo();
    }

    @RequestMapping("/name")
    @ResponseBody
    public String getName() {
        return userService.getUserNameById(1);
    }

    @RequestMapping("/name1")
    @ResponseBody
    public String getName1() {
        return userService0.getName();
    }

    @RequestMapping("/names")
    @ResponseBody
    public String getAllNames() {
        return String.join(", ", userService.getAllUserNames());
    }
}