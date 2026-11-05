package com.omni.business.assistance.autotest.core.engine;

import com.omni.business.assistance.autotest.model.TestStep;

public class DefaultStepExecutor extends StepExecutor {
    private final ActionHandler actionHandler;

    public DefaultStepExecutor(String actionType) {
        this.actionHandler = ActionHandlerRegistry.getHandler(actionType);
        if (this.actionHandler == null) {
            throw new IllegalArgumentException("No handler for action: " + actionType);
        }
    }

    @Override
    protected Object doExecute(TestStep step, Context context) throws Exception {
        return actionHandler.execute(step, context);
    }


}
