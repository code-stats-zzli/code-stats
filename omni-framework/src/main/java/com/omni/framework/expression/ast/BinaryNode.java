package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

public class BinaryNode extends ExpressionNode {
    private final ExpressionNode left;
    private final ExpressionNode right;
    private final String operator;

    public BinaryNode(ExpressionNode left, ExpressionNode right, String operator) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    public ExpressionNode getLeft() {
        return left;
    }

    public ExpressionNode getRight() {
        return right;
    }

    public String getOperator() {
        return operator;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitBinary(this, context);
    }
}