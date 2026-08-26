package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

public class AssignmentNode extends ExpressionNode {
    private final VariableNode variable;
    private final ExpressionNode value;

    public AssignmentNode(VariableNode variable, ExpressionNode value) {
        this.variable = variable;
        this.value = value;
    }

    public VariableNode getVariable() {
        return variable;
    }

    public ExpressionNode getValue() {
        return value;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitAssignment(this, context);
    }
}