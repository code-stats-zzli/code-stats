package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

public class ArrayAccessNode extends ExpressionNode {
    private final ExpressionNode target;
    private final ExpressionNode index;

    public ArrayAccessNode(ExpressionNode target, ExpressionNode index) {
        this.target = target;
        this.index = index;
    }

    public ExpressionNode getTarget() {
        return target;
    }

    public ExpressionNode getIndex() {
        return index;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitArrayAccess(this, context);
    }
}