package com.omni.business.assistance.table.mysql.controller;

import com.omni.business.assistance.table.mysql.dto.UserCreateRequest;
import com.omni.business.assistance.table.mysql.dto.UserResponse;
import com.omni.business.assistance.table.mysql.dto.UserUpdateRequest;
import com.omni.business.assistance.table.mysql.service.UserService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.swag.annotation.ApiModule;
import com.omni.framework.swag.annotation.ApiOperation;
import com.omni.framework.util.JsonUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiModule(value = "用户管理", description = "提供用户信息的增删改查接口，支持 @RequestBody 接收对象")
@Controller
@RequestMapping("/user/manage")
public class UserManageController {

    @Autowired
    private UserService userService;

    /**
     * 根据ID查询用户
     * GET /user/manage/get/{id}
     */
    @ApiOperation(value = "获取用户信息", notes = "根据用户ID返回用户详情")
    @RequestMapping("/get/{id}")
    @ResponseBody
    public String getUserById(Integer id) {
        Map<String, Object> result = new HashMap<>();
        if (id == null || id <= 0) {
            result.put("success", false);
            result.put("error", "无效的用户ID");
            return JsonUtil.toJson(result);
        }
        UserResponse user = userService.getUserById(id);
        if (user == null) {
            result.put("success", false);
            result.put("error", "用户不存在");
        } else {
            result.put("success", true);
            result.put("data", user);
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 查询所有用户
     * GET /user/manage/list
     */
    @ApiOperation(value = "获取所有用户列表", notes = "返回所有用户信息列表")
    @RequestMapping("/list")
    @ResponseBody
    public String listUsers() {
        Map<String, Object> result = new HashMap<>();
        List<UserResponse> users = userService.listAllUsers();
        result.put("success", true);
        result.put("data", users);
        return JsonUtil.toJson(result);
    }

    /**
     * 创建用户
     * POST /user/manage/create
     * Body: {"name":"张三","age":25}
     */
    @ApiOperation(value = "新增用户", notes = "传入用户名称和年龄，使用 @RequestBody 接收")
    @RequestMapping("/create")
    @ResponseBody
    public String createUser(@RequestBody UserCreateRequest createReq) {
        Map<String, Object> result = new HashMap<>();
        if (createReq.getName() == null || createReq.getName().trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "用户名不能为空");
            return JsonUtil.toJson(result);
        }
        boolean ok = userService.createUser(createReq);
        result.put("success", ok);
        if (!ok) {
            result.put("error", "创建用户失败");
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 更新用户
     * PUT /user/manage/update
     * Body: {"id":1,"name":"李四","age":30}
     */
    @ApiOperation(value = "更新用户信息", notes = "根据用户ID更新名称和年龄，使用 @RequestBody 接收")
    @RequestMapping("/update")
    @ResponseBody
    public String updateUser(@RequestBody UserUpdateRequest updateReq) {
        Map<String, Object> result = new HashMap<>();
        if (updateReq.getId() == null || updateReq.getId() <= 0) {
            result.put("success", false);
            result.put("error", "无效的用户ID");
            return JsonUtil.toJson(result);
        }
        boolean ok = userService.updateUser(updateReq);
        result.put("success", ok);
        if (!ok) {
            result.put("error", "更新用户失败，可能用户不存在");
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 删除用户
     * DELETE /user/manage/delete/{id}
     */
    @ApiOperation(value = "删除用户", notes = "根据用户ID删除用户")
    @RequestMapping("/delete/{id}")
    @ResponseBody
    public String deleteUser(Integer id) {
        Map<String, Object> result = new HashMap<>();
        if (id == null || id <= 0) {
            result.put("success", false);
            result.put("error", "无效的用户ID");
            return JsonUtil.toJson(result);
        }
        boolean ok = userService.deleteUser(id);
        result.put("success", ok);
        if (!ok) {
            result.put("error", "删除用户失败，可能用户不存在");
        }
        return JsonUtil.toJson(result);
    }
}