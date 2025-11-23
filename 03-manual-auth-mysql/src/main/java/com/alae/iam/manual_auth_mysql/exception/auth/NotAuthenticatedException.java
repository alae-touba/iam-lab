package com.alae.iam.manual_auth_mysql.exception.auth;

public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException(String message) {
        super(message);
    }
}