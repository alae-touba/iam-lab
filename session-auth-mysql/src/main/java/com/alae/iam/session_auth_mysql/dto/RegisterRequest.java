package com.alae.iam.session_auth_mysql.dto;

public record RegisterRequest(String username, String email, String password) {
}
