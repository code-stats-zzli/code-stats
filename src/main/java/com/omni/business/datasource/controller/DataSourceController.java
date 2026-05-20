package com.omni.business.datasource.controller;

import com.omni.business.datasource.service.DataSourceConfigService;
import com.omni.business.datasource.service.DriverJarService;
import com.omni.common.datasource.model.DataSourceConfig;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/datasource/api")
public class DataSourceController {

    @Autowired
    private DataSourceConfigService service;

    @Autowired
    private DriverJarService driverService;

    @RequestMapping("/list")
    @ResponseBody
    public String list() throws Exception {
        List<DataSourceConfig> list = service.listAll();
        return JsonUtil.toJson(list);
    }

    @RequestMapping("/save")
    @ResponseBody
    public String save(@RequestBody DataSourceConfig config) throws Exception {
        boolean ok = service.save(config);
        return JsonUtil.toJson(Map.of("success", ok));
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(String id) throws Exception {
        boolean ok = service.delete(id);
        return JsonUtil.toJson(Map.of("success", ok));
    }

    @RequestMapping("/test")
    @ResponseBody
    public String testConnection(@RequestBody DataSourceConfig config) {
        boolean ok = service.testConnection(config);
        return JsonUtil.toJson(Map.of("success", ok, "message", ok ? "连接成功" : "连接失败"));
    }

    // 新增：获取驱动列表（供前端下拉选择）
    @RequestMapping("/drivers")
    @ResponseBody
    public String listDrivers() throws Exception {
        return JsonUtil.toJson(driverService.listAll());
    }
}