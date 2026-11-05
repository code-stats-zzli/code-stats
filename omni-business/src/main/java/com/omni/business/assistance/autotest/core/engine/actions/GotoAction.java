package com.omni.business.assistance.autotest.core.engine.actions;

import com.omni.business.assistance.autotest.core.engine.ActionHandler;
import com.omni.business.assistance.autotest.core.engine.Context;
import com.omni.business.assistance.autotest.core.engine.VariableResolver;
import com.omni.business.assistance.autotest.model.TestStep;

public class GotoAction implements ActionHandler {
    @Override
    public Object execute(TestStep step, Context context) throws Exception {
        String url = step.getActionValue();
        url = VariableResolver.resolve(url, context.getVariables());
        // 模拟浏览器导航（实际可调用 Selenium/Playwright）
        context.setCurrentUrl(url);
        return "Navigated to " + url;
    }
}
