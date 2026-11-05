package com.omni.business.assistance.table.mysql.service.impl;

import com.omni.business.assistance.table.mysql.service.IUserService;
import com.omni.framework.spring.annotation.Service;

@Service
public class UserServiceImpl2 implements IUserService {
    @Override
    public String getName() {
        return "2";
    }
}
