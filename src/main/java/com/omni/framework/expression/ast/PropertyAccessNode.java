package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

public class PropertyAccessNode extends ExpressionNode {
    private final ExpressionNode target;
    private final String property;

    public PropertyAccessNode(ExpressionNode target, String property) {
        this.target = target;
        this.property = property;
    }

    public ExpressionNode getTarget() {
        return target;
    }

    public String getProperty() {
        return property;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitPropertyAccess(this, context);
    }
}