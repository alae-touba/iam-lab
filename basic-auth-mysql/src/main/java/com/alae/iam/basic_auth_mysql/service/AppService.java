package com.alae.iam.basic_auth_mysql.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AppService {
    public String getHomePage(Authentication auth) {
        if (auth == null) {
            return "Welcome, Guest!";
        }
        return "Welcome, " + auth.getName();
    }
}
