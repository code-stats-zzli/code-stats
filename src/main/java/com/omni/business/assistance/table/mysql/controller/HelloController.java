package com.omni.business.assistance.table.mysql.controller;

import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.ModelAndView;

@Controller
@RequestMapping("/hello")
public class HelloController {

    @RequestMapping("/text")
    @ResponseBody
    public String sayHello(@RequestParam("name") String name) {
        return "Hello, " + name + "! (from @ResponseBody)";
    }

    @RequestMapping("/add")
    @ResponseBody
    public int add(@RequestParam("a") int a, @RequestParam("b") int b) {
        return a + b;
    }

    @RequestMapping("/html")
    public ModelAndView sayHtml(@RequestParam("name") String name) {
        ModelAndView mv = new ModelAndView("greeting");
        mv.addAttribute("user", name);
        return mv;
    }
}
