package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.service.SysLogService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统日志 REST 接口。
 *
 * <pre>
 * GET /sys/api/log/files                                    → 日志文件列表
 * GET /sys/api/log/tail?file=app.log&amp;lines=500              → 最后 N 行 + offset
 * GET /sys/api/log/read?file=app.log&amp;offset=12345           → 从 offset 读增量
 * </pre>
 *
 * <p>后续其他系统功能（JVM / 线程 / 进程 / 配置 / 命令执行等），
 * 请在 com.omni.business.assistance.sys.controller 下新建独立 Controller，
 * 保持单一职责，不要堆积到本类。</p>
 */
@Controller
@RequestMapping("/sys/api/log")
public class SysLogController {

    @Autowired
    private SysLogService service;

    @RequestMapping("/files")
    @ResponseBody
    public String files() {
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", true);
            r.put("dir", service.getLogDir().toString());
            r.put("files", service.listFiles());
            return JsonUtil.toJson(r);
        } catch (Exception e) {
            return err(e);
        }
    }

    @RequestMapping("/tail")
    @ResponseBody
    public String tail(
            @RequestParam("file") String file,
            @RequestParam(value = "lines", required = false, defaultValue = "500") int lines) {
        try {
            Map<String, Object> r = service.tail(file, lines);
            r.put("success", true);
            return JsonUtil.toJson(r);
        } catch (Exception e) {
            return err(e);
        }
    }

    @RequestMapping("/read")
    @ResponseBody
    public String read(
            @RequestParam("file") String file,
            @RequestParam(value = "offset", required = false, defaultValue = "0") long offset) {
        try {
            Map<String, Object> r = service.readFrom(file, offset);
            r.put("success", true);
            return JsonUtil.toJson(r);
        } catch (Exception e) {
            return err(e);
        }
    }

    private String err(Exception e) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("error", e == null ? "未知错误"
                : e.getClass().getSimpleName() + ": " + e.getMessage());
        return JsonUtil.toJson(r);
    }
}