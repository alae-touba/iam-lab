package com.alae.iam.manual_auth_mysql.exception.auth;

public class AccountLockedException extends RuntimeException {

    private final String username;

    public AccountLockedException(String message) {
        super(message);
        this.username = null;
    }

    public AccountLockedException(String message, String username) {
        super(message);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}