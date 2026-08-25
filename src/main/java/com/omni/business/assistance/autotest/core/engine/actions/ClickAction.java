package com.omni.business.assistance.autotest.core.engine.actions;

import com.omni.business.assistance.autotest.core.engine.ActionHandler;
import com.omni.business.assistance.autotest.core.engine.Context;
import com.omni.business.assistance.autotest.core.engine.VariableResolver;
import com.omni.business.assistance.autotest.model.TestStep;

public class ClickAction implements ActionHandler {
    @Override
    public Object execute(TestStep step, Context context) throws Exception {
        String locator = step.getLocatorValue();
        locator = VariableResolver.resolve(locator, context.getVariables());
        // 模拟点击
        return "Clicked on " + locator;
    }
}