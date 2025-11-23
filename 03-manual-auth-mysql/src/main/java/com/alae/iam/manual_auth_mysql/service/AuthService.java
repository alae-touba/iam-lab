package com.alae.iam.manual_auth_mysql.service;

import com.alae.iam.manual_auth_mysql.domain.AppUser;
import com.alae.iam.manual_auth_mysql.domain.AuthPrincipal;
import com.alae.iam.manual_auth_mysql.dto.UserResponse;
import com.alae.iam.manual_auth_mysql.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final SecurityContextRepository securityContextRepository;
  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;

  public UserResponse completeLogin(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    request.getSession(true); // ensure session is created so cookie is issued
    securityContextRepository.saveContext(context, request, response);
    SecurityContextHolder.setContext(context);
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return new UserResponse(principal.id(), principal.username(), principal.email());
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
  }

  public AppUser register(String username, String email, String rawPassword) {
    if (appUserRepository.findByUsername(username).isPresent()) {
      throw new IllegalArgumentException("Username already exists");
    }
    if (appUserRepository.findByEmail(email).isPresent()) {
      throw new IllegalArgumentException("Email already exists");
    }

    AppUser user = AppUser.builder()
        .username(username)
        .email(email)
        .passwordHash(passwordEncoder.encode(rawPassword))
        .enabled(true)
        .accountLocked(false)
        .build();

    return appUserRepository.save(user);
  }
}
