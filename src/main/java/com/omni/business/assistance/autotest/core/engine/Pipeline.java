package com.omni.business.assistance.autotest.core.engine;

import com.omni.autotest.core.model.TestCase;

import com.omni.business.assistance.autotest.core.engine.*;
import com.omni.business.assistance.autotest.mapper.TestAssertionMapper;
import com.omni.business.assistance.autotest.mapper.TestStepMapper;
import com.omni.business.assistance.autotest.model.TestAssertion;
import com.omni.business.assistance.autotest.model.TestStep;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.List;

public class Pipeline {
    public static ExecutionResult execute(TestCase testCase, Context context) throws Exception {
        TestStepMapper stepMapper = SpringContextHolder.getApplicationContext().getBeanFactory()
                .getBean(TestStepMapper.class);
        TestAssertionMapper assertionMapper = SpringContextHolder.getApplicationContext().getBeanFactory()
                .getBean(TestAssertionMapper.class);

        List<TestStep> steps = stepMapper.selectByCaseId(testCase.getId());
        List<TestAssertion> assertions = assertionMapper.selectByCaseId(testCase.getId());

        ExecutionResult result = new ExecutionResult(testCase.getId());
        for (TestStep step : steps) {
            if (context.isStopped()) break;
            StepExecutor executor = StepExecutorRegistry.getExecutor(step.getActionType());
            if (executor == null) {
                throw new RuntimeException("No executor for action: " + step.getActionType());
            }
            StepResult stepResult = executor.execute(step, context, assertions);
            result.addStepResult(stepResult);
        }
        result.setEndTime(System.currentTimeMillis());
        return result;
    }
}