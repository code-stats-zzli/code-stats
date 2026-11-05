package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

import java.util.List;

/**
 * 数组常量节点，如 [1, 2, "hello", true]
 */
public class ArrayLiteralNode extends ExpressionNode {
    private final List<ExpressionNode> elements;

    public ArrayLiteralNode(List<ExpressionNode> elements) {
        this.elements = elements;
    }

    public List<ExpressionNode> getElements() {
        return elements;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitArrayLiteral(this, context);
    }
}