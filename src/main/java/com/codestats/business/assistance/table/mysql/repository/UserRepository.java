package com.codestats.business.assistance.table.mysql.repository;


import com.framework.spring.annotation.Repository;

@Repository
public class UserRepository {

    public String findUser() {
        return "Tom (mfrom repository)";
    }
}
