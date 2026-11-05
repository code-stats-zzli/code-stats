package com.omni.business.assistance.autotest.core.engine;


import com.omni.business.assistance.autotest.core.engine.Context;
import com.omni.business.assistance.autotest.model.TestAssertion;
import com.omni.business.assistance.autotest.model.TestStep;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.List;

public abstract class StepExecutor {
    protected static final Logger log = LoggerFactory.getLogger(StepExecutor.class);

    public final StepResult execute(TestStep step, Context context, List<TestAssertion> assertions) {
        StepResult result = new StepResult(step.getId(), step.getStepOrder());
        long start = System.currentTimeMillis();
        try {
            beforeExecute(step, context);
            Object actionResult = doExecute(step, context);
            result.setActionResult(actionResult);
            afterExecute(step, context, actionResult);
            // execute assertions
            for (TestAssertion assertion : assertions) {
                if (assertion.getStepId() == null || assertion.getStepId().equals(step.getId())) {
                    AssertionResult ar = executeAssertion(assertion, context);
                    result.getAssertionResults().add(ar);
                    if (!ar.isPassed() && assertion.getFailHandle() == 1) {
                        result.setPassed(false);
                        break;
                    }
                }
            }
            result.setPassed(true);
        } catch (Exception e) {
            log.error("Step execution failed", e);
            result.setPassed(false);
            result.setError(e);
            handleFailure(step, context, e);
        }
        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
    }

    protected void beforeExecute(TestStep step, Context context) throws Exception {
        // 处理等待
        if ("sleep".equals(step.getWaitType()) && step.getWaitSeconds() > 0) {
            Thread.sleep(step.getWaitSeconds() * 1000L);
        }
        // 变量替换
        if (step.getActionValue() != null) {
            step.setActionValue(VariableResolver.resolve(step.getActionValue(), context.getVariables()));
        }
    }

    protected abstract Object doExecute(TestStep step, Context context) throws Exception;

    protected void afterExecute(TestStep step, Context context, Object result) throws Exception {
        // 可提取变量
    }

    protected AssertionResult executeAssertion(TestAssertion assertion, Context context) throws Exception {
        return AssertionHandlerRegistry.getHandler(assertion.getAssertType()).execute(assertion, context);
    }

    protected void handleFailure(TestStep step, Context context, Exception e) {
        if (step.getFailHandle() == 1) {
            context.setStopped(true);
        }
    }
}