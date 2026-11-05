package com.omni.business.assistance.autotest.core.engine;


import com.omni.business.assistance.autotest.model.TestAssertion;

public interface AssertionHandler {
    AssertionResult execute(TestAssertion assertion, Context context) throws Exception;
}