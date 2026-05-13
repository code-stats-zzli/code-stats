package com.framework.spring.mvc;

import com.framework.tomcat.connector.Response;

public class ViewResolver {
    // 实际生产可转发到 JSP，这里仅演示输出到控制台
    public void render(ModelAndView mv, Response response) throws Exception {
        // 将模型数据写入响应（简单拼接）
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h1>View: ").append(mv.getViewName()).append("</h1>");
        for (java.util.Map.Entry<String, Object> entry : mv.getModel().entrySet()) {
            sb.append("<p>").append(entry.getKey()).append(" = ").append(entry.getValue()).append("</p>");
        }
        sb.append("</body></html>");
        response.write(sb.toString());
    }
}