package com.omni.business.assistance.table.mysql.repository;


import com.omni.framework.spring.annotation.Repository;

@Repository
public class UserRepository {

    public String findUser() {
        return "Tom (mfrom repository)";
    }
}
