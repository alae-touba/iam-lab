package com.alae.iam.basic_auth_mysql.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.alae.iam.basic_auth_mysql.dto.RegisterRequest;
import com.alae.iam.basic_auth_mysql.dto.RegisterResponse;
import com.alae.iam.basic_auth_mysql.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
  }
}
