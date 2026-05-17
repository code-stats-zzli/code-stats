// 文件路径: src/main/java/com/omni/business/assistance/table/mysql/dto/UserResponse.java
package com.omni.business.assistance.table.mysql.dto;

public class UserResponse {
    private Integer id;
    private String name;
    private Integer age;

    public UserResponse() {
    }

    public UserResponse(Integer id, String name, Integer age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}