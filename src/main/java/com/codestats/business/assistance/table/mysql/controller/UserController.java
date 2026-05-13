package com.codestats.business.assistance.table.mysql.controller;

import com.codestats.business.assistance.table.mysql.service.UserService;
import com.framework.spring.annotation.Autowired;
import com.framework.spring.annotation.Controller;
import com.framework.spring.annotation.RequestMapping;
import com.framework.spring.annotation.ResponseBody;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @RequestMapping("/info")
    public String getUserInfo() {
        return userService.getUserInfo();
    }

    @RequestMapping("/name")
    @ResponseBody
    public String getName() {
        return userService.getUserNameById(1);
    }

    @RequestMapping("/names")
    @ResponseBody
    public String getAllNames() {
        return String.join(", ", userService.getAllUserNames());
    }
}