package com.omni.business.assistance.autotest.service;

import com.omni.autotest.core.model.TestCase;

import com.omni.business.assistance.autotest.mapper.*;
import com.omni.business.assistance.autotest.model.*;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TestCaseService {

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private TestStepMapper stepMapper;

    @Autowired
    private TestAssertionMapper assertionMapper;

    @Autowired
    private TestVariableMapper variableMapper;

    @Autowired
    private TestHookMapper hookMapper;

    @Autowired
    private TestDataMapper dataMapper;

    // ==================== 用例 CRUD ====================

    public TestCase getById(String id) {
        return testCaseMapper.selectById(id);
    }

    public List<TestCase> listByProject(String projectId) {
        return testCaseMapper.selectByProjectId(projectId);
    }

    /**
     * 保存用例（新增或更新）
     * @param testCase 用例对象
     * @return 用例ID
     */
    public String save(TestCase testCase) {
        if (testCase.getId() == null || testCase.getId().isEmpty()) {
            // 新增
            testCase.setId(UUID.randomUUID().toString());
            testCaseMapper.insert(testCase);
        } else {
            // 更新
            testCaseMapper.update(testCase);
        }
        return testCase.getId();
    }

    public void delete(String id) {
        // 删除用例，级联删除由数据库外键处理，但也可手动清理关联数据
        testCaseMapper.delete(id);
        // 可选：手动删除关联数据（如果外键未设置级联）
        // stepMapper.deleteByCaseId(id);
        // assertionMapper.deleteByCaseId(id);
        // variableMapper.deleteByCaseId(id);
        // hookMapper.deleteByCaseId(id);
    }

    // ==================== 步骤管理 ====================

    public List<TestStep> loadSteps(String caseId) {
        return stepMapper.selectByCaseId(caseId);
    }

    public void saveStep(TestStep step) {
        if (step.getId() == null || step.getId().isEmpty()) {
            step.setId(UUID.randomUUID().toString());
            stepMapper.insert(step);
        } else {
            stepMapper.update(step);
        }
    }

    public void deleteStep(String id) {
        stepMapper.delete(id);
    }

    public void deleteStepsByCaseId(String caseId) {
        stepMapper.deleteByCaseId(caseId);
    }

    // ==================== 断言管理 ====================

    public List<TestAssertion> loadAssertions(String caseId) {
        return assertionMapper.selectByCaseId(caseId);
    }

    public void saveAssertion(TestAssertion assertion) {
        if (assertion.getId() == null || assertion.getId().isEmpty()) {
            assertion.setId(UUID.randomUUID().toString());
            assertionMapper.insert(assertion);
        } else {
            assertionMapper.update(assertion);
        }
    }

    public void deleteAssertion(String id) {
        assertionMapper.delete(id);
    }

    public void deleteAssertionsByCaseId(String caseId) {
        assertionMapper.deleteByCaseId(caseId);
    }

    // ==================== 变量管理 ====================

    public List<TestVariable> loadVariables(String caseId) {
        return variableMapper.selectByCaseId(caseId);
    }

    public void saveVariable(TestVariable variable) {
        if (variable.getId() == null || variable.getId().isEmpty()) {
            variable.setId(UUID.randomUUID().toString());
            variableMapper.insert(variable);
        }
        // 变量更新暂不支持，或者可扩展 update
    }

    public void deleteVariablesByCaseId(String caseId) {
        variableMapper.deleteByCaseId(caseId);
    }

    // ==================== 前后置钩子管理 ====================

    public List<TestHook> loadHooks(String caseId) {
        return hookMapper.selectByCaseId(caseId);
    }

    public void saveHook(TestHook hook) {
        if (hook.getId() == null || hook.getId().isEmpty()) {
            hook.setId(UUID.randomUUID().toString());
            hookMapper.insert(hook);
        }
        // 更新暂不实现，可扩展
    }

    public void deleteHooksByCaseId(String caseId) {
        hookMapper.deleteByCaseId(caseId);
    }

    // ==================== 测试数据管理 ====================

    public List<TestData> loadData(String caseId, Integer envType) {
        return dataMapper.selectByCaseIdAndEnv(caseId, envType);
    }

    public void saveData(TestData data) {
        if (data.getId() == null || data.getId().isEmpty()) {
            data.setId(UUID.randomUUID().toString());
            dataMapper.insert(data);
        }
        // 更新暂不实现
    }
}