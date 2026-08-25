package com.omni.business.assistance.autotest.core.engine.assertions;

import com.omni.business.assistance.autotest.core.engine.AssertionHandler;
import com.omni.business.assistance.autotest.core.engine.AssertionResult;
import com.omni.business.assistance.autotest.core.engine.Context;
import com.omni.business.assistance.autotest.core.engine.VariableResolver;
import com.omni.business.assistance.autotest.model.TestAssertion;

public class AssertUrlHandler implements AssertionHandler {
    @Override
    public AssertionResult execute(TestAssertion assertion, Context context) throws Exception {
        String expected = assertion.getExpectedValue();
        expected = VariableResolver.resolve(expected, context.getVariables());
        String actual = context.getCurrentUrl();
        AssertionResult result = new AssertionResult(assertion.getId());
        result.setExpected(expected);
        result.setActual(actual);
        boolean passed = expected.equals(actual);
        result.setPassed(passed);
        result.setMessage(passed ? "URL匹配" : "URL不匹配");
        return result;
    }
}
