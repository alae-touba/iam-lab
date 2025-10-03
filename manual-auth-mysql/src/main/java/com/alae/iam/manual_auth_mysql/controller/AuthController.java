package com.alae.iam.manual_auth_mysql.controller;

import com.alae.iam.manual_auth_mysql.domain.AuthPrincipal;
import com.alae.iam.manual_auth_mysql.dto.LoginRequest;
import com.alae.iam.manual_auth_mysql.dto.LoginResponse;
import com.alae.iam.manual_auth_mysql.dto.RegisterRequest;
import com.alae.iam.manual_auth_mysql.dto.UserResponse;
import com.alae.iam.manual_auth_mysql.exception.auth.AccountDisabledException;
import com.alae.iam.manual_auth_mysql.exception.auth.AccountLockedException;
import com.alae.iam.manual_auth_mysql.exception.auth.InvalidCredentialsException;
import com.alae.iam.manual_auth_mysql.exception.auth.NotAuthenticatedException;
import com.alae.iam.manual_auth_mysql.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
    Authentication token = new UsernamePasswordAuthenticationToken(req.usernameOrEmail(), req.password());
    try {
      Authentication auth = authenticationManager.authenticate(token);
      return ResponseEntity.ok(authService.completeLogin(auth, request, response));
    } catch (BadCredentialsException e) {
      throw new InvalidCredentialsException("Invalid username/email or password", req.usernameOrEmail());
    } catch (LockedException e) {
      throw new AccountLockedException("Account is locked", req.usernameOrEmail());
    } catch (DisabledException e) {
      throw new AccountDisabledException("Account is disabled", req.usernameOrEmail());
    }
  }

  @GetMapping("/me")
  public ResponseEntity<LoginResponse> me() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      throw new NotAuthenticatedException("User is not authenticated");
    }
    AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
    return ResponseEntity.ok(new LoginResponse(p.id(), p.username(), p.email()));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(request, response);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
    var user = authService.register(req.username(), req.email(), req.password());
    var response = new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
