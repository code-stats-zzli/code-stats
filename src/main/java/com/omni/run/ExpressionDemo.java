package com.omni.run;

import com.omni.framework.expression.*;
import com.omni.framework.expression.function.BuiltinFunctions;
import com.omni.framework.expression.function.FunctionResolver;
import com.omni.framework.expression.resolver.impl.ColumnMappingResolver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpressionDemo {
    public static void main(String[] args) {
        EvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("user", new User("张三", 25));
        ctx.setVariable("list", java.util.Arrays.asList(10, 20, 30));
        BuiltinFunctions.registerAll(ctx);

        ExpressionParser parser = new StandardExpressionParser();// ============ 1. 显式变量（直接放数据包顶层） ============
        Map<String, Object> data = new HashMap<>();
        data.put("price", 99.9);          // ← 显式变量
        data.put("qty", 5);               // ← 显式变量
        Object result = ExpressionFactory.eval("price * qty", data);
// 输出：499.5


// ============ 2. Map 数据源（使用 __resolvers.map） ============
        Map<String, Object> rowMap = new HashMap<>();
        rowMap.put("name", "张三");
        rowMap.put("age", 25);
        rowMap.put("salary", 15000);

        Map<String, Object> data2 = new HashMap<>();
        data2.put("taxRate", 0.1);        // 显式变量
        data2.put("__resolvers", Map.of(
                "map", rowMap                 // ★ Map 数据源
        ));

        Object result2 = ExpressionFactory.eval("name + '年龄' + age + '，税后：' + salary*(1-taxRate)", data2);
// 输出：张三年龄25，税后：13500.0


// ============ 3. 混合使用：显式变量 + Map + 根对象 ============
        User user = new User("李四", 30);

        Map<String, Object> rowMap2 = new HashMap<>();
        rowMap2.put("salary", 20000);

        Map<String, Object> data3 = new HashMap<>();
        data3.put("bonus", 5000);          // 显式变量
        data3.put("__resolvers", Map.of(
                "root", user,                  // 根对象
                "map", rowMap2                 // Map 数据源
        ));

// 优先级：显式变量 > root > map
        Object result3 = ExpressionFactory.eval("name + '年龄' + age + '，薪资' + salary + '，奖金' + bonus", data3);
// 输出：李四年龄30，薪资20000，奖金5000


// ============ 4. 列映射 + Map + 表格 全量混合 ============
        Map<String, String> mapping = Map.of("n", "name", "s", "salary");
        Map<String, Object> rowMap3 = Map.of("bonus", 8000);
        List<String> headers = Arrays.asList("name", "salary");
        Object[] row = new Object[]{"王五", 12000};

        Map<String, Object> data4 = new HashMap<>();
        data4.put("taxRate", 0.1);
        data4.put("__resolvers", Map.of(
                "root", new User("赵六", 28),
                "map", rowMap3,
                "table", Map.of("headers", headers, "row", row),
                "functions", Map.of("double", (FunctionResolver) a -> (double) a[0] * 2),
                "custom", Arrays.asList(new ColumnMappingResolver(mapping))
        ));

// 查找顺序：显式变量(taxRate) → custom(列映射) → root → map → table
        String expr = "n + '年龄' + age + '，薪资' + s + '，奖金' + bonus + '，双倍奖金：' + double(bonus)";
        Object result4 = ExpressionFactory.eval(expr, data4);
// 输出：赵六年龄28，薪资12000，奖金8000，双倍奖金：16000.0
    }

    static class User {
        private String name;
        private int age;

        public User(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }
}