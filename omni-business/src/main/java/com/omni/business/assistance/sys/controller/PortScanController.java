package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.model.PortScanRequest;
import com.omni.business.assistance.sys.model.PortScanResult;
import com.omni.business.assistance.sys.service.PortScanService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 远程端口/服务探测接口。
 *
 * <pre>
 * POST /sys/api/ssh/port-scan
 * Body: { "connectionId": "xxx" }
 * →
 * {
 *   "success": true,
 *   "data": {
 *     "connectionId": "xxx", "host": "192.168.1.10", "osType": "linux",
 *     "durationMs": 123,
 *     "ports": [ { "port": 22, "protocol": "TCP", "state": "LISTEN",
 *                  "address": "0.0.0.0", "processPid": 1234,
 *                  "processName": "sshd", "serviceName": "SSH",
 *                  "serviceCategory": "系统服务" }, ... ],
 *     "summary": { "total": 10, "tcpCount": 8, "udpCount": 2,
 *                  "byCategory": {...}, "byService": {...} }
 *   }
 * }
 * </pre>
 */
@Controller
@RequestMapping("/sys/api/ssh")
public class PortScanController {

    @Autowired
    private PortScanService portScanService;

    @RequestMapping("/port-scan")
    @ResponseBody
    public String scan(@RequestBody PortScanRequest req) {
        try {
            PortScanResult result = portScanService.scan(req);
            Map<String, Object> out = new LinkedHashMap<>();
            if (result.getError() != null) {
                out.put("success", true);
                out.put("data", result);
                out.put("warning", result.getError());
            } else {
                out.put("success", true);
                out.put("data", result);
            }
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", false);
            out.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return JsonUtil.toJson(out);
        }
    }
}