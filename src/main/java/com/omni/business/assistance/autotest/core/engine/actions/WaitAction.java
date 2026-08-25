package com.omni.business.assistance.autotest.core.engine.actions;

import com.omni.business.assistance.autotest.core.engine.ActionHandler;
import com.omni.business.assistance.autotest.core.engine.Context;
import com.omni.business.assistance.autotest.model.TestStep;

public class WaitAction implements ActionHandler {
    @Override
    public Object execute(TestStep step, Context context) throws Exception {
        int seconds = step.getWaitSeconds() != null ? step.getWaitSeconds() : 0;
        if (seconds > 0) {
            Thread.sleep(seconds * 1000L);
        }
        return "Waited " + seconds + "s";
    }
}
