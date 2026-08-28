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
      /*  // 1. 数字运算（不再是字符串拼接）
        System.out.println(ExpressionEngine.eval("1 + 2 * 3"));          // 7.0
        System.out.println(ExpressionEngine.eval("10 / 3"));             // 3.3333333333333335
        System.out.println(ExpressionEngine.eval("'a' + 1"));            // "a1"（字符串拼接保留）

        // 2. 数组常量
        System.out.println(ExpressionEngine.eval("[1, 2, 3][1]"));       // 2
        System.out.println(ExpressionEngine.eval("[[1,2], [3,4]][1][0]")); // 3

        // 3. 混合数组
        System.out.println(ExpressionEngine.eval("[\"hello\", 123, true][2]")); // true

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

        Object result2 = ExpressionEngine.eval("name + '年龄' + age + '，税后：' + salary*(1-taxRate)", data2);
// 输出：张三年龄25，税后：13500.0
        System.out.println("result2:"+result2);


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
        Object result3 = ExpressionEngine.eval("name + '年龄' + age + '，薪资' + salary + '，奖金' + bonus", data3);
// 输出：李四年龄30，薪资20000，奖金5000

*/
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
        Object result4 = ExpressionEngine.eval(expr, data4);
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