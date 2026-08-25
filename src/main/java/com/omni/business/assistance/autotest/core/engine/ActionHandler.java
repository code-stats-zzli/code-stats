package com.omni.business.assistance.autotest.core.engine;


import com.omni.business.assistance.autotest.model.TestStep;

public interface ActionHandler {
    Object execute(TestStep step, Context context) throws Exception;
}