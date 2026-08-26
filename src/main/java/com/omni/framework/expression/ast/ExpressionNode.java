package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

public abstract class ExpressionNode {
    public abstract Object accept(Evaluator visitor, EvaluationContext context);
}