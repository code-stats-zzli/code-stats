# 自研 IoC 容器设计文档

## 1. 概述

IoC（Inversion of Control）容器是框架的核心，负责 Bean 的注册、实例化、依赖注入和生命周期管理。本实现参考 Spring IoC 容器，但代码更精简，便于教学。

## 2. 核心接口与类

| 类/接口 | 职责 |
|---------|------|
| `BeanFactory` | 定义获取 Bean 的基本方法 |
| `ListableBeanFactory` | 增加按类型获取 Bean 的能力 |
| `ConfigurableBeanFactory` | 增加 BeanPostProcessor 注册和销毁单例 |
| `ConfigurableListableBeanFactory` | 合并上述接口，增加预实例化单例 |
| `BeanDefinition` | 存储 Bean 的元信息（类名、作用域、是否懒加载等） |
| `DefaultListableBeanFactory` | 默认实现，维护 BeanDefinition 和单例缓存 |
| `AbstractAutowireCapableBeanFactory` | 实现 Bean 的创建、属性填充、初始化前后处理 |

## 3. 启动流程

1. `AnnotationConfigApplicationContext` 扫描指定包下的所有类。
2. 识别 `@Component`, `@Service`, `@Controller` 等注解，生成 `BeanDefinition` 并注册到 `BeanDefinitionRegistry`。
3. 调用 `refresh()` 方法：
    - 准备 BeanFactory
    - 执行 `BeanFactoryPostProcessor`
    - 注册 `BeanPostProcessor`
    - 预实例化所有非懒加载的单例 Bean
4. 依赖注入通过反射设置 `@Autowired` 字段，支持 `@Primary` 和 `@Qualifier`。

## 4. 依赖注入解析规则

- 如果只有一个候选 Bean，直接注入。
- 多个候选时优先匹配 `@Qualifier` 指定的名称，其次匹配字段名，最后选择 `@Primary` 标记的 Bean。

## 5. 扩展点

- `BeanPostProcessor`: 可在 Bean 初始化前后进行额外处理（如 AOP 代理）。
- `BeanFactoryPostProcessor`: 可在 Bean 实例化前修改 BeanDefinition。

## 6. 使用示例

```java
@Component
public class MyService {
    @Autowired
    private UserRepository userRepo;
    // ...
}