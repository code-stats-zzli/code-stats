package com.omni.business.assistance.table.mysql.controller;

import com.omni.business.assistance.table.mysql.service.IUserService;
import com.omni.business.assistance.table.mysql.service.UserService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.swag.annotation.ApiModule;
import com.omni.framework.swag.annotation.ApiOperation;


@ApiModule(value = "UserController", description = "UserController")
@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private IUserService userService0;


    @ApiOperation(value = "获取用户信息", notes = "获取用户信息")
    @RequestMapping("/info")
    public String getUserInfo() {
        return userService.getUserInfo();
    }

    @ApiOperation(value = "获取用户名称", notes = "根据用户 ID 返回用户名")
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