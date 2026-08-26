package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

public class UnaryNode extends ExpressionNode {
    private final ExpressionNode operand;
    private final String operator;

    public UnaryNode(ExpressionNode operand, String operator) {
        this.operand = operand;
        this.operator = operator;
    }

    public ExpressionNode getOperand() {
        return operand;
    }

    public String getOperator() {
        return operator;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitUnary(this, context);
    }
}