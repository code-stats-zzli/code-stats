package com.omni.framework.expression.evaluator;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.ExpressionException;
import com.omni.framework.expression.ast.*;
import com.omni.framework.expression.util.ReflectUtils;
import com.omni.framework.expression.util.TypeUtils;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

public class StandardEvaluator implements Evaluator {

    @Override
    public Object visitLiteral(LiteralNode node, EvaluationContext context) {
        return node.getValue();
    }

    @Override
    public Object visitVariable(VariableNode node, EvaluationContext context) {
        Object val = context.getVariable(node.getName());
        if (val == null) {
            throw new ExpressionException("Variable not found: " + node.getName());
        }
        return val;
    }

    @Override
    public Object visitUnary(UnaryNode node, EvaluationContext context) {
        Object operand = evaluate(node.getOperand(), context);
        String op = node.getOperator();
        switch (op) {
            case "-": return TypeUtils.negate(operand);
            case "+": return TypeUtils.plus(operand);
            case "!": return !TypeUtils.toBoolean(operand);
            case "~": return TypeUtils.bitwiseNot(operand);
            default: throw new ExpressionException("Unsupported unary operator: " + op);
        }
    }

    @Override
    public Object visitBinary(BinaryNode node, EvaluationContext context) {
        Object left = evaluate(node.getLeft(), context);
        Object right = evaluate(node.getRight(), context);
        String op = node.getOperator();
        switch (op) {
            case "+": return TypeUtils.add(left, right);
            case "-": return TypeUtils.subtract(left, right);
            case "*": return TypeUtils.multiply(left, right);
            case "/": return TypeUtils.divide(left, right);
            case "%": return TypeUtils.modulus(left, right);
            case "&&": return TypeUtils.toBoolean(left) && TypeUtils.toBoolean(right);
            case "||": return TypeUtils.toBoolean(left) || TypeUtils.toBoolean(right);
            case "==": return TypeUtils.equals(left, right);
            case "!=": return !TypeUtils.equals(left, right);
            case "<": return TypeUtils.compare(left, right) < 0;
            case "<=": return TypeUtils.compare(left, right) <= 0;
            case ">": return TypeUtils.compare(left, right) > 0;
            case ">=": return TypeUtils.compare(left, right) >= 0;
            case "&": return TypeUtils.bitwiseAnd(left, right);
            case "|": return TypeUtils.bitwiseOr(left, right);
            case "^": return TypeUtils.bitwiseXor(left, right);
            case "<<": return TypeUtils.shiftLeft(left, right);
            case ">>": return TypeUtils.shiftRight(left, right);
            case ">>>": return TypeUtils.unsignedShiftRight(left, right);
            default: throw new ExpressionException("Unsupported binary operator: " + op);
        }
    }

    @Override
    public Object visitTernary(TernaryNode node, EvaluationContext context) {
        boolean condition = TypeUtils.toBoolean(evaluate(node.getCondition(), context));
        return evaluate(condition ? node.getThenBranch() : node.getElseBranch(), context);
    }

    @Override
    public Object visitMethodCall(MethodCallNode node, EvaluationContext context) {
        Object target = null;
        if (node.getTarget() != null) {
            target = evaluate(node.getTarget(), context);
        }
        Object[] args = node.getArguments().stream()
                .map(arg -> evaluate(arg, context))
                .toArray();

        // 如果有 target，优先调用实例方法
        if (target != null) {
            return ReflectUtils.invokeMethod(target, node.getName(), args);
        }

        // 无 target，尝试上下文函数，再尝试静态方法
        try {
            return context.callFunction(node.getName(), args);
        } catch (ExpressionException e) {
            return ReflectUtils.invokeStaticMethod(node.getName(), args);
        }
    }

    @Override
    public Object visitPropertyAccess(PropertyAccessNode node, EvaluationContext context) {
        Object target = evaluate(node.getTarget(), context);
        return context.getProperty(target, node.getProperty());
    }

    @Override
    public Object visitAssignment(AssignmentNode node, EvaluationContext context) {
        Object value = evaluate(node.getValue(), context);
        context.setVariable(node.getVariable().getName(), value);
        return value;
    }

    @Override
    public Object visitArrayAccess(ArrayAccessNode node, EvaluationContext context) {
        Object target = evaluate(node.getTarget(), context);
        Object index = evaluate(node.getIndex(), context);
        if (target == null) {
            throw new ExpressionException("Cannot access index of null");
        }
        if (target.getClass().isArray()) {
            int idx = TypeUtils.toInteger(index);
            return Array.get(target, idx);
        } else if (target instanceof List) {
            int idx = TypeUtils.toInteger(index);
            return ((List<?>) target).get(idx);
        } else if (target instanceof Map) {
            return ((Map<?, ?>) target).get(index);
        } else {
            throw new ExpressionException("Unsupported array access target: " + target.getClass());
        }
    }

    public Object evaluate(ExpressionNode node, EvaluationContext context) {
        return node.accept(this, context);
    }
}