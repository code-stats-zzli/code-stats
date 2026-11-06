package com.omni.framework.mybatis;

import com.omni.framework.mybatis.annotation.*;

/**
 * Mapper 通用 CRUD 接口。
 *
 * <p>子接口只需声明 {@code @Table("table_name")} 和 {@code @Id} 字段即可复用。</p>
 * <p>所有方法由 {@link com.omni.framework.mybatis.proxy.MapperProxy} 在运行时生成 SQL。</p>
 *
 * <pre>
 *   &#64;Mapper
 *   public interface UserMapper extends BaseMapper&lt;User&gt; {
 *       // 自定义查询仍可写
 *       &#64;Select("SELECT name FROM user")
 *       List&lt;String&gt; getAllNames();
 *   }
 * </pre>
 */
public interface BaseMapper<T> {

    /** 按主键查询 */
    T selectById(Object id);

    /** 全表查询 */
    java.util.List<T> selectAll();

    /** 插入（主键为 null 时由调用方赋值） */
    int insert(T entity);

    /** 按主键更新（只更新非 null 字段由子类约定，此处全字段更新） */
    int updateById(T entity);

    /** 按主键删除 */
    int deleteById(Object id);
}