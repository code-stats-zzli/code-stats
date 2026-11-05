package com.omni.business.assistance.autotest.core.engine.actions;

import com.omni.business.assistance.autotest.core.engine.ActionHandler;
import com.omni.business.assistance.autotest.core.engine.Context;
import com.omni.business.assistance.autotest.core.engine.VariableResolver;
import com.omni.business.assistance.autotest.model.TestStep;

public class InputAction implements ActionHandler {
    @Override
    public Object execute(TestStep step, Context context) throws Exception {
        String value = step.getActionValue();
        value = VariableResolver.resolve(value, context.getVariables());
        String locator = step.getLocatorValue();
        locator = VariableResolver.resolve(locator, context.getVariables());
        // 模拟输入
        return "Input '" + value + "' into " + locator;
    }
}
