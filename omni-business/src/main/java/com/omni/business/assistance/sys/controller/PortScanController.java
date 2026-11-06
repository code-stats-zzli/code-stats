package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.model.PortScanRequest;
import com.omni.business.assistance.sys.model.PortScanResult;
import com.omni.business.assistance.sys.service.PortScanService;
import com.omni.business.common.util.BaseController;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/** ★ L1-01 */
@Controller
@RequestMapping("/sys/api/ssh")
public class PortScanController extends BaseController {

    @Autowired
    private PortScanService portScanService;

    @RequestMapping("/port-scan")
    @ResponseBody
    public String scan(@RequestBody PortScanRequest req) {
        try {
            PortScanResult result = portScanService.scan(req);
            Map<String, Object> out = new LinkedHashMap<>();
            if (result.getError() != null) {
                out.put("data", result);
                out.put("warning", result.getError());
            } else {
                out.put("data", result);
            }
            return okFlat(out);
        } catch (Exception e) {
            return error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}