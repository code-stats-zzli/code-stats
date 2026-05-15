package com.codestats.business.assistance.table.mysql.repository;


import com.codestats.framework.spring.annotation.Repository;

@Repository
public class UserRepository {

    public String findUser() {
        return "Tom (mfrom repository)";
    }
}
