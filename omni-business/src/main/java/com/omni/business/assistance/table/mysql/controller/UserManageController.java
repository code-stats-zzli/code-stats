package com.omni.business.assistance.table.mysql.controller;

import com.omni.business.assistance.table.mysql.dto.UserCreateRequest;
import com.omni.business.assistance.table.mysql.dto.UserResponse;
import com.omni.business.assistance.table.mysql.dto.UserUpdateRequest;
import com.omni.business.assistance.table.mysql.service.UserService;
import com.omni.business.common.util.BaseController;
import com.omni.common.util.StringUtils;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.swag.annotation.ApiModule;
import com.omni.framework.swag.annotation.ApiOperation;
import com.omni.framework.spring.annotation.PathVariable;
import com.omni.framework.util.JsonUtil;

import java.util.List;

@ApiModule(value = "用户管理", description = "提供用户信息的增删改查接口，支持 @RequestBody 接收对象")
@Controller
@RequestMapping("/user/manage")
public class UserManageController extends BaseController {

    @Autowired
    private UserService userService;

    @ApiOperation(value = "获取用户信息", notes = "根据用户ID返回用户详情")
    @RequestMapping("/get/{id}")
    @ResponseBody
    public String getUserById(@PathVariable("id") Integer id) {
        if (id == null || id <= 0) return error("无效的用户ID");
        UserResponse user = userService.getUserById(id);
        if (user == null) return error("用户不存在");
        return JsonUtil.toJson(user);
    }

    @ApiOperation(value = "获取所有用户列表")
    @RequestMapping("/list")
    @ResponseBody
    public String listUsers() {
        List<UserResponse> users = userService.listAllUsers();
        return JsonUtil.toJson(users);
    }

    @ApiOperation(value = "新增用户")
    @RequestMapping("/create")
    @ResponseBody
    public String createUser(@RequestBody UserCreateRequest createReq) {
        if (StringUtils.isBlank(createReq.getName())) return error("用户名不能为空");
        boolean ok = userService.createUser(createReq);
        return ok ? ok() : error("创建用户失败");
    }

    @ApiOperation(value = "更新用户信息")
    @RequestMapping("/update")
    @ResponseBody
    public String updateUser(@RequestBody UserUpdateRequest updateReq) {
        if (updateReq.getId() == null || updateReq.getId() <= 0) return error("无效的用户ID");
        boolean ok = userService.updateUser(updateReq);
        return ok ? ok() : error("更新用户失败，可能用户不存在");
    }

    @ApiOperation(value = "删除用户")
    @RequestMapping("/delete/{id}")
    @ResponseBody
    public String deleteUser(@PathVariable("id") Integer id) {
        if (id == null || id <= 0) return error("无效的用户ID");
        boolean ok = userService.deleteUser(id);
        return ok ? ok() : error("删除用户失败，可能用户不存在");
    }
}