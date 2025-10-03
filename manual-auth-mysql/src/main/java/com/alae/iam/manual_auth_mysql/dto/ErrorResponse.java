package com.alae.iam.manual_auth_mysql.dto;

/**
 * @deprecated Use Spring Boot's ProblemDetail (RFC 7807) instead for standardized error responses.
 * This class is kept for backward compatibility but should not be used in new code.
 */
@Deprecated
public record ErrorResponse(String error) {}
