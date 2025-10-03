package com.alae.iam.manual_auth_mysql.exception.auth;

public class AccountDisabledException extends RuntimeException {

    private final String username;

    public AccountDisabledException(String message) {
        super(message);
        this.username = null;
    }

    public AccountDisabledException(String message, String username) {
        super(message);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}