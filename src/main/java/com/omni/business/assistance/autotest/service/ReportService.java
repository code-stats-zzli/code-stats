package com.omni.business.assistance.autotest.service;

import com.omni.business.assistance.autotest.mapper.TestExecutionMapper;
import com.omni.business.assistance.autotest.mapper.TestSnapshotMapper;
import com.omni.business.assistance.autotest.model.TestExecution;
import com.omni.business.assistance.autotest.model.TestSnapshot;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.List;

@Service
public class ReportService {
    @Autowired
    private TestExecutionMapper executionMapper;
    @Autowired
    private TestSnapshotMapper snapshotMapper;

    public TestExecution getExecution(String executionId) {
        return executionMapper.selectById(executionId);
    }

    public List<TestSnapshot> getSnapshots(String executionId) {
        return snapshotMapper.selectByExecutionId(executionId);
    }

    // 可扩展生成HTML报告等
}
