package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

public class TernaryNode extends ExpressionNode {
    private final ExpressionNode condition;
    private final ExpressionNode thenBranch;
    private final ExpressionNode elseBranch;

    public TernaryNode(ExpressionNode condition, ExpressionNode thenBranch, ExpressionNode elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public ExpressionNode getCondition() {
        return condition;
    }

    public ExpressionNode getThenBranch() {
        return thenBranch;
    }

    public ExpressionNode getElseBranch() {
        return elseBranch;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitTernary(this, context);
    }
}