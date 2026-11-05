package com.omni.business.assistance.three3d.service;

import com.omni.framework.spring.annotation.Service;
import com.omni.framework.web.TraceContextHolder;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TestChainService {
    private static final AtomicLong counter = new AtomicLong(0);

    public String executeTestChain() {
        long threadId = Thread.currentThread().getId();
        // 模拟请求上下文（通常由 DispatcherServlet 自动创建，这里手动模拟）
        TraceContextHolder.putContext(threadId, "/test-call-chain");
        TraceContextHolder.TraceContext ctx = TraceContextHolder.getContext(threadId);
        ctx.appendNode("TestController.execute");

        try {
            // 模拟业务调用
            step1();
            step2();
            step3();
            // 模拟耗时
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 标记完成
            TraceContextHolder.updateContext(threadId, null, "End");
            // 注意：实际生产由 DispatcherServlet 清理，此处保留一会儿让监控捕获
        }
        return "test-" + counter.incrementAndGet();
    }

    private void step1() {
        TraceContextHolder.getCurrentContext().appendNode("TestService.step1");
        sleep(300);
        step1_1();
    }
    private void step1_1() {
        TraceContextHolder.getCurrentContext().appendNode("TestService.step1_1");
        sleep(200);
    }
    private void step2() {
        TraceContextHolder.getCurrentContext().appendNode("TestMapper.query");
        sleep(500);
    }
    private void step3() {
        TraceContextHolder.getCurrentContext().appendNode("JdbcTemplate.execute");
        sleep(400);
    }
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}