package com.omni.framework.expression.evaluator;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.ast.*;

public interface Evaluator {
    default Object evaluate(ExpressionNode node, EvaluationContext context) {
        return node.accept(this, context);
    }

    Object visitLiteral(LiteralNode node, EvaluationContext context);
    Object visitVariable(VariableNode node, EvaluationContext context);
    Object visitUnary(UnaryNode node, EvaluationContext context);
    Object visitBinary(BinaryNode node, EvaluationContext context);
    Object visitTernary(TernaryNode node, EvaluationContext context);
    Object visitMethodCall(MethodCallNode node, EvaluationContext context);
    Object visitPropertyAccess(PropertyAccessNode node, EvaluationContext context);
    Object visitAssignment(AssignmentNode node, EvaluationContext context);
    Object visitArrayAccess(ArrayAccessNode node, EvaluationContext context);
}