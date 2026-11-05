package com.omni.business.assistance.autotest.service;


import com.omni.business.assistance.autotest.core.engine.Context;
import com.omni.business.assistance.autotest.core.engine.ExecutionResult;
import com.omni.business.assistance.autotest.core.engine.Pipeline;
import com.omni.business.assistance.autotest.mapper.TestExecutionMapper;
import com.omni.business.assistance.autotest.model.TestExecution;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class TestExecutionService {

    @Autowired
    private TestCaseService testCaseService;

    @Autowired
    private TestExecutionMapper executionMapper;

    public TestExecution execute(String caseId, String triggerUser) throws Exception {
        com.omni.autotest.core.model.TestCase testCase = testCaseService.getById(caseId);
        Context context = new Context();
        // 加载数据集等
        ExecutionResult result = Pipeline.execute(testCase, context);

        TestExecution execution = new TestExecution();
        execution.setId(UUID.randomUUID().toString());
        execution.setCaseId(caseId);
        execution.setExecutionCode("EXEC-" + System.currentTimeMillis());
        execution.setTriggerUser(triggerUser);
        execution.setStartTime(new Date(result.getStartTime()));
        execution.setEndTime(new Date(System.currentTimeMillis()));
        execution.setTotalSteps(result.getStepResults().size());
        long passCount = result.getStepResults().stream().filter(stepResult -> stepResult.isPassed()).count();
        execution.setPassCount((int) passCount);
        execution.setFailCount(result.getStepResults().size() - (int) passCount);
        execution.setPassRate(execution.getTotalSteps() > 0 ? (passCount * 100.0 / execution.getTotalSteps()) : 0);
        execution.setStatus(execution.getFailCount() == 0 ? 1 : 2);
        execution.setTotalTimeMs((int)(System.currentTimeMillis() - result.getStartTime()));

        executionMapper.insert(execution);
        return execution;
    }

    public List<TestExecution> listByCase(String caseId) {
        return executionMapper.selectByCaseId(caseId);
    }
}