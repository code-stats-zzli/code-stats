// 文件路径: src/main/java/com/omni/business/assistance/table/mysql/dto/UserCreateRequest.java
package com.omni.business.assistance.table.mysql.dto;

public class UserCreateRequest {
    private String name;
    private Integer age;

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