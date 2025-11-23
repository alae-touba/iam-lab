package com.alae.iam.manual_auth_mysql.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String usernameOrEmail,
    @NotBlank String password
) {}
