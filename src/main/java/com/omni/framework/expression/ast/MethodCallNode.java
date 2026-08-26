package com.omni.framework.expression.ast;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.evaluator.Evaluator;

import java.util.List;

public class MethodCallNode extends ExpressionNode {
    private final String name;
    private final List<ExpressionNode> arguments;
    private final ExpressionNode target; // 可为null表示静态方法或上下文函数

    public MethodCallNode(String name, List<ExpressionNode> arguments) {
        this(name, arguments, null);
    }

    public MethodCallNode(String name, List<ExpressionNode> arguments, ExpressionNode target) {
        this.name = name;
        this.arguments = arguments;
        this.target = target;
    }

    public String getName() {
        return name;
    }

    public List<ExpressionNode> getArguments() {
        return arguments;
    }

    public ExpressionNode getTarget() {
        return target;
    }

    public boolean isStaticCall() {
        return target == null;
    }

    @Override
    public Object accept(Evaluator visitor, EvaluationContext context) {
        return visitor.visitMethodCall(this, context);
    }
}