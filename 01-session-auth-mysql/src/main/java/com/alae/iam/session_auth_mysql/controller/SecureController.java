package com.alae.iam.session_auth_mysql.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/secure")
public class SecureController {

    @GetMapping("/ping")
    public Map<String, Boolean> ping() {
        Map<String, Boolean> response = new HashMap<>();
        response.put("pong", true);
        return response;
    }
}