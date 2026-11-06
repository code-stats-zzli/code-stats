package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.service.SysLogService;
import com.omni.business.common.util.BaseController;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.spring.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/** ★ L1-01 */
@Controller
@RequestMapping("/sys/api/log")
public class SysLogController extends BaseController {

    @Autowired
    private SysLogService service;

    @RequestMapping("/files")
    @ResponseBody
    public String files() {
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("dir", service.getLogDir().toString());
            r.put("files", service.listFiles());
            return okFlat(r);
        } catch (Exception e) { return error(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    @RequestMapping("/tail")
    @ResponseBody
    public String tail(@RequestParam("file") String file,
                       @RequestParam(value = "lines", required = false, defaultValue = "500") int lines) {
        try {
            Map<String, Object> r = service.tail(file, lines);
            return okFlat(r);
        } catch (Exception e) { return error(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    @RequestMapping("/read")
    @ResponseBody
    public String read(@RequestParam("file") String file,
                       @RequestParam(value = "offset", required = false, defaultValue = "0") long offset) {
        try {
            Map<String, Object> r = service.readFrom(file, offset);
            return okFlat(r);
        } catch (Exception e) { return error(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }
}