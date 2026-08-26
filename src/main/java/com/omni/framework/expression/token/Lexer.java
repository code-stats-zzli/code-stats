package com.omni.framework.expression.token;

import com.omni.framework.expression.ExpressionException;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String source;
    private int pos = 0;
    private final int length;

    public Lexer(String source) {
        this.source = source;
        this.length = source.length();
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < length) {
            char ch = source.charAt(pos);
            if (Character.isWhitespace(ch)) {
                pos++;
                continue;
            }
            if (Character.isJavaIdentifierStart(ch)) {
                tokens.add(scanIdentifier());
                continue;
            }
            if (Character.isDigit(ch) || (ch == '.' && pos + 1 < length && Character.isDigit(source.charAt(pos + 1)))) {
                tokens.add(scanNumber());
                continue;
            }
            if (ch == '"' || ch == '\'') {
                tokens.add(scanString());
                continue;
            }

            // 多字符运算符
            String twoChar = pos + 1 < length ? source.substring(pos, pos + 2) : "";
            switch (twoChar) {
                case "==": case "!=": case "&&": case "||": case "<=": case ">=":
                case "<<": case ">>": case ">>>":
                    tokens.add(new Token(TokenType.OPERATOR, twoChar));
                    pos += 2;
                    continue;
            }

            // 单字符
            if ("+-*/%^&|~!<>=?:,()[].".indexOf(ch) >= 0) {
                TokenType type = TokenType.OPERATOR;
                if (ch == '(') type = TokenType.LPAREN;
                else if (ch == ')') type = TokenType.RPAREN;
                else if (ch == '[') type = TokenType.LBRACKET;
                else if (ch == ']') type = TokenType.RBRACKET;
                else if (ch == ',') type = TokenType.COMMA;
                else if (ch == '.') type = TokenType.DOT;
                tokens.add(new Token(type, String.valueOf(ch)));
                pos++;
                continue;
            }

            throw new ExpressionException("Unexpected character: " + ch);
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private Token scanIdentifier() {
        int start = pos;
        while (pos < length && Character.isJavaIdentifierPart(source.charAt(pos))) {
            pos++;
        }
        String value = source.substring(start, pos);
        // 检查是否关键字
        if ("true".equals(value) || "false".equals(value)) {
            return new Token(TokenType.BOOLEAN, value);
        }
        if ("null".equals(value)) {
            return new Token(TokenType.NULL, value);
        }
        return new Token(TokenType.IDENTIFIER, value);
    }

    private Token scanNumber() {
        int start = pos;
        boolean hasDot = false;
        while (pos < length) {
            char ch = source.charAt(pos);
            if (Character.isDigit(ch)) {
                pos++;
            } else if (ch == '.' && !hasDot && pos + 1 < length && Character.isDigit(source.charAt(pos + 1))) {
                hasDot = true;
                pos++;
            } else {
                break;
            }
        }
        return new Token(TokenType.NUMBER, source.substring(start, pos));
    }

    private Token scanString() {
        char quote = source.charAt(pos);
        pos++;
        int start = pos;
        while (pos < length && source.charAt(pos) != quote) {
            if (source.charAt(pos) == '\\') {
                pos++; // skip escape char
            }
            pos++;
        }
        if (pos >= length) {
            throw new ExpressionException("Unterminated string literal");
        }
        String value = source.substring(start, pos);
        pos++; // skip closing quote
        return new Token(TokenType.STRING, value);
    }
}