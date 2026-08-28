package com.omni.framework.expression;

import com.omni.framework.expression.ast.*;
import com.omni.framework.expression.evaluator.StandardEvaluator;
import com.omni.framework.expression.token.Lexer;
import com.omni.framework.expression.token.Token;
import com.omni.framework.expression.token.TokenType;

import java.util.ArrayList;
import java.util.List;

public class StandardExpressionParser implements ExpressionParser {
    private List<Token> tokens;
    private int pos;

    @Override
    public Expression parse(String expression) {
        Lexer lexer = new Lexer(expression);
        this.tokens = lexer.tokenize();
        this.pos = 0;
        ExpressionNode node = parseExpression();
        expect(TokenType.EOF);
        return new ExpressionImpl(expression, node);
    }

    // ---------- 核心递归下降解析 ----------

    private ExpressionNode parseExpression() {
        ExpressionNode node = parseLogicalOr();
        if (match(TokenType.OPERATOR, "?")) {
            ExpressionNode condition = node;
            ExpressionNode thenBranch = parseExpression();
            expect(TokenType.OPERATOR, ":");
            ExpressionNode elseBranch = parseExpression();
            return new TernaryNode(condition, thenBranch, elseBranch);
        }
        if (match(TokenType.OPERATOR, "=")) {
            if (!(node instanceof VariableNode)) {
                throw new ExpressionException("Left side of assignment must be a variable");
            }
            ExpressionNode value = parseExpression();
            return new AssignmentNode((VariableNode) node, value);
        }
        return node;
    }

    private ExpressionNode parseLogicalOr() {
        ExpressionNode node = parseLogicalAnd();
        while (match(TokenType.OPERATOR, "||")) {
            String op = previous().getValue();
            ExpressionNode right = parseLogicalAnd();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseLogicalAnd() {
        ExpressionNode node = parseBitwiseOr();
        while (match(TokenType.OPERATOR, "&&")) {
            String op = previous().getValue();
            ExpressionNode right = parseBitwiseOr();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseBitwiseOr() {
        ExpressionNode node = parseBitwiseXor();
        while (match(TokenType.OPERATOR, "|")) {
            String op = previous().getValue();
            ExpressionNode right = parseBitwiseXor();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseBitwiseXor() {
        ExpressionNode node = parseBitwiseAnd();
        while (match(TokenType.OPERATOR, "^")) {
            String op = previous().getValue();
            ExpressionNode right = parseBitwiseAnd();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseBitwiseAnd() {
        ExpressionNode node = parseEquality();
        while (match(TokenType.OPERATOR, "&")) {
            String op = previous().getValue();
            ExpressionNode right = parseEquality();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseEquality() {
        ExpressionNode node = parseRelational();
        while (match(TokenType.OPERATOR, "==", "!=")) {
            String op = previous().getValue();
            ExpressionNode right = parseRelational();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseRelational() {
        ExpressionNode node = parseShift();
        while (match(TokenType.OPERATOR, "<", "<=", ">", ">=")) {
            String op = previous().getValue();
            ExpressionNode right = parseShift();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseShift() {
        ExpressionNode node = parseAdditive();
        while (match(TokenType.OPERATOR, "<<", ">>", ">>>")) {
            String op = previous().getValue();
            ExpressionNode right = parseAdditive();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseAdditive() {
        ExpressionNode node = parseMultiplicative();
        while (match(TokenType.OPERATOR, "+", "-")) {
            String op = previous().getValue();
            ExpressionNode right = parseMultiplicative();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseMultiplicative() {
        ExpressionNode node = parseUnary();
        while (match(TokenType.OPERATOR, "*", "/", "%")) {
            String op = previous().getValue();
            ExpressionNode right = parseUnary();
            node = new BinaryNode(node, right, op);
        }
        return node;
    }

    private ExpressionNode parseUnary() {
        if (match(TokenType.OPERATOR, "!", "~", "+", "-")) {
            String op = previous().getValue();
            ExpressionNode operand = parseUnary();
            return new UnaryNode(operand, op);
        }
        return parsePrimary();
    }

    // ★★★★★ 核心修复：parsePrimary 中完成类型转换 + 支持数组常量 ★★★★★
    private ExpressionNode parsePrimary() {
        // 1. 数字常量 -> 转为 Integer / Long / Double
        if (match(TokenType.NUMBER)) {
            String val = previous().getValue();
            Object lit;
            if (val.contains(".") || val.contains("E") || val.contains("e")) {
                lit = Double.valueOf(val);
            } else {
                try {
                    lit = Integer.valueOf(val);
                } catch (NumberFormatException e) {
                    lit = Long.valueOf(val);
                }
            }
            return parsePostfix(new LiteralNode(lit));
        }

        // 2. 字符串常量
        if (match(TokenType.STRING)) {
            return parsePostfix(new LiteralNode(previous().getValue()));
        }

        // 3. 布尔常量
        if (match(TokenType.BOOLEAN)) {
            return parsePostfix(new LiteralNode(Boolean.valueOf(previous().getValue())));
        }

        // 4. null 常量
        if (match(TokenType.NULL)) {
            return parsePostfix(new LiteralNode(null));
        }

        // 5. ★★★ 新增：数组常量 [expr1, expr2, ...] ★★★
        if (match(TokenType.LBRACKET)) {
            List<ExpressionNode> elements = new ArrayList<>();
            if (!check(TokenType.RBRACKET)) {
                do {
                    elements.add(parseExpression());
                } while (match(TokenType.COMMA));
            }
            expect(TokenType.RBRACKET);
            return parsePostfix(new ArrayLiteralNode(elements));
        }

        // 6. 括号表达式
        if (match(TokenType.LPAREN)) {
            ExpressionNode node = parseExpression();
            expect(TokenType.RPAREN);
            return parsePostfix(node);
        }

        // 7. 标识符（变量 / 函数调用）
        if (match(TokenType.IDENTIFIER)) {
            Token id = previous();
            if (match(TokenType.LPAREN)) {
                List<ExpressionNode> args = parseArguments();
                expect(TokenType.RPAREN);
                return parsePostfix(new MethodCallNode(id.getValue(), args));
            } else {
                return parsePostfix(new VariableNode(id.getValue()));
            }
        }

        throw new ExpressionException("Unexpected token: " + peek().getValue());
    }

    private ExpressionNode parsePostfix(ExpressionNode node) {
        while (true) {
            if (match(TokenType.DOT)) {
                Token prop = expect(TokenType.IDENTIFIER);
                if (match(TokenType.LPAREN)) {
                    List<ExpressionNode> args = parseArguments();
                    expect(TokenType.RPAREN);
                    node = new MethodCallNode(prop.getValue(), args, node);
                } else {
                    node = new PropertyAccessNode(node, prop.getValue());
                }
                continue;
            }
            if (match(TokenType.LBRACKET)) {
                ExpressionNode index = parseExpression();
                expect(TokenType.RBRACKET);
                node = new ArrayAccessNode(node, index);
                continue;
            }
            break;
        }
        return node;
    }

    private List<ExpressionNode> parseArguments() {
        List<ExpressionNode> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        return args;
    }

    // ---------- 辅助方法 ----------

    private Token peek() {
        if (pos < tokens.size()) return tokens.get(pos);
        return null;
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }

    private boolean match(TokenType type) {
        if (pos < tokens.size() && tokens.get(pos).getType() == type) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean match(TokenType type, String... values) {
        if (pos < tokens.size() && tokens.get(pos).getType() == type) {
            for (String val : values) {
                if (tokens.get(pos).getValue().equals(val)) {
                    pos++;
                    return true;
                }
            }
        }
        return false;
    }

    private Token expect(TokenType type) {
        if (pos < tokens.size() && tokens.get(pos).getType() == type) {
            return tokens.get(pos++);
        }
        throw new ExpressionException("Expected " + type + " but found " + (peek() != null ? peek().getValue() : "EOF"));
    }

    private Token expect(TokenType type, String value) {
        if (pos < tokens.size() && tokens.get(pos).getType() == type && tokens.get(pos).getValue().equals(value)) {
            return tokens.get(pos++);
        }
        throw new ExpressionException("Expected '" + value + "' but found " + (peek() != null ? peek().getValue() : "EOF"));
    }

    private boolean check(TokenType type) {
        return pos < tokens.size() && tokens.get(pos).getType() == type;
    }

    // 内部表达式实现
    private static class ExpressionImpl implements Expression {
        private final String expression;
        private final ExpressionNode root;

        ExpressionImpl(String expression, ExpressionNode root) {
            this.expression = expression;
            this.root = root;
        }

        @Override
        public Object getValue(EvaluationContext context) {
            return root.accept(new StandardEvaluator(), context);
        }

        @Override
        public String getExpressionString() {
            return expression;
        }
    }
}