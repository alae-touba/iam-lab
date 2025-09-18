package com.alae.iam.basic_auth_mysql.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 100) 
    String username,
    @NotBlank @Email 
    String email,
    @NotBlank @Size(min = 8, max = 100) 
    String password
) {}
