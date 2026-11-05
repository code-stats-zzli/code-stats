package com.omni.business.assistance.table.mysql.service.impl;

import com.omni.business.assistance.table.mysql.service.IUserService;
import com.omni.framework.spring.annotation.Primary;
import com.omni.framework.spring.annotation.Service;

@Primary
@Service
public class UserServiceImpl1 implements IUserService {
    @Override
    public String getName() {
        return "1";
    }
}
