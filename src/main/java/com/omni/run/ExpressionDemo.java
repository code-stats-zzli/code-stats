package com.omni.run;

import com.omni.framework.expression.EvaluationContext;
import com.omni.framework.expression.Expression;
import com.omni.framework.expression.ExpressionParser;
import com.omni.framework.expression.StandardEvaluationContext;
import com.omni.framework.expression.StandardExpressionParser;
import com.omni.framework.expression.function.BuiltinFunctions;

public class ExpressionDemo {
    public static void main(String[] args) {
        EvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("user", new User("张三", 25));
        ctx.setVariable("list", java.util.Arrays.asList(10, 20, 30));
        BuiltinFunctions.registerAll(ctx);

        ExpressionParser parser = new StandardExpressionParser();
        Expression expr = parser.parse("user.age + 10 > 30 ? '成年人' : '青少年'");
        System.out.println(expr.getValue(ctx)); // 成年人

        Expression expr2 = parser.parse("max(list[0], list[1]) + user.age");
        System.out.println(expr2.getValue(ctx)); // 20 + 25 = 45

        Expression expr3 = parser.parse("user.name.length()");
        System.out.println(expr3.getValue(ctx)); // 2

        Expression expr4 = parser.parse("user.name.toUpperCase()");
        System.out.println(expr4.getValue(ctx)); // "张三"

        // 赋值
        Expression assign = parser.parse("x = 100");
        assign.getValue(ctx);
        System.out.println(ctx.getVariable("x")); // 100
    }

    static class User {
        private String name;
        private int age;

        public User(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }
}