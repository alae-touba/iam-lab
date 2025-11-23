package com.alae.iam.manual_auth_mysql.exception;

import com.alae.iam.manual_auth_mysql.exception.auth.AccountDisabledException;
import com.alae.iam.manual_auth_mysql.exception.auth.AccountLockedException;
import com.alae.iam.manual_auth_mysql.exception.auth.InvalidCredentialsException;
import com.alae.iam.manual_auth_mysql.exception.auth.NotAuthenticatedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    problemDetail.setTitle("Invalid Credentials");
    problemDetail.setType(URI.create("https://api.manual-auth-mysql.com/errors/invalid-credentials"));
    problemDetail.setProperty("timestamp", Instant.now());
    problemDetail.setProperty("errorCode", "INVALID_CREDENTIALS");
    if (ex.getUsernameAttempted() != null) {
      problemDetail.setProperty("usernameAttempted", ex.getUsernameAttempted());
    }
    return problemDetail;
  }

  @ExceptionHandler(AccountLockedException.class)
  public ProblemDetail handleAccountLocked(AccountLockedException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, ex.getMessage());
    problemDetail.setTitle("Account Locked");
    problemDetail.setType(URI.create("https://api.manual-auth-mysql.com/errors/account-locked"));
    problemDetail.setProperty("timestamp", Instant.now());
    problemDetail.setProperty("errorCode", "ACCOUNT_LOCKED");
    if (ex.getUsername() != null) {
      problemDetail.setProperty("username", ex.getUsername());
    }
    return problemDetail;
  }

  @ExceptionHandler(AccountDisabledException.class)
  public ProblemDetail handleAccountDisabled(AccountDisabledException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problemDetail.setTitle("Account Disabled");
    problemDetail.setType(URI.create("https://api.manual-auth-mysql.com/errors/account-disabled"));
    problemDetail.setProperty("timestamp", Instant.now());
    problemDetail.setProperty("errorCode", "ACCOUNT_DISABLED");
    if (ex.getUsername() != null) {
      problemDetail.setProperty("username", ex.getUsername());
    }
    return problemDetail;
  }

  @ExceptionHandler(NotAuthenticatedException.class)
  public ProblemDetail handleNotAuthenticated(NotAuthenticatedException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    problemDetail.setTitle("Not Authenticated");
    problemDetail.setType(URI.create("https://api.manual-auth-mysql.com/errors/not-authenticated"));
    problemDetail.setProperty("timestamp", Instant.now());
    problemDetail.setProperty("errorCode", "NOT_AUTHENTICATED");
    return problemDetail;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    problemDetail.setTitle("Validation Error");
    problemDetail.setType(URI.create("https://api.manual-auth-mysql.com/errors/validation-error"));
    problemDetail.setProperty("timestamp", Instant.now());
    problemDetail.setProperty("errorCode", "VALIDATION_ERROR");

    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error ->
      fieldErrors.put(error.getField(), error.getDefaultMessage())
    );
    problemDetail.setProperty("fieldErrors", fieldErrors);

    return problemDetail;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGeneric(Exception ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problemDetail.setTitle("Internal Server Error");
    problemDetail.setType(URI.create("https://api.manual-auth-mysql.com/errors/server-error"));
    problemDetail.setProperty("timestamp", Instant.now());
    problemDetail.setProperty("errorCode", "SERVER_ERROR");
    // Don't expose sensitive information
    return problemDetail;
  }
}
