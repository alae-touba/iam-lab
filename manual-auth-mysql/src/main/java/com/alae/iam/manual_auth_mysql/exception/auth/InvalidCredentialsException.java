package com.alae.iam.manual_auth_mysql.exception.auth;

public class InvalidCredentialsException extends RuntimeException {

    private final String usernameAttempted;

    public InvalidCredentialsException(String message) {
        super(message);
        this.usernameAttempted = null;
    }

    public InvalidCredentialsException(String message, String usernameAttempted) {
        super(message);
        this.usernameAttempted = usernameAttempted;
    }

    public String getUsernameAttempted() {
        return usernameAttempted;
    }
}