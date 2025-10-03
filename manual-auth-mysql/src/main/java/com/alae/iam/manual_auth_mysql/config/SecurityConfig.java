package com.alae.iam.manual_auth_mysql.config;

import com.alae.iam.manual_auth_mysql.auth.CustomAuthenticationProvider;
import com.alae.iam.manual_auth_mysql.domain.AppUser;
import com.alae.iam.manual_auth_mysql.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  @Bean
  @Lazy
  public CustomAuthenticationProvider customAuthenticationProvider(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
    return new CustomAuthenticationProvider(userRepository, passwordEncoder);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomAuthenticationProvider customAuthenticationProvider) throws Exception {
    http
      .formLogin(AbstractHttpConfigurer::disable)
      .httpBasic(AbstractHttpConfigurer::disable)
      .csrf(AbstractHttpConfigurer::disable) // learning mode (enable later)
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
      .exceptionHandling(e -> e
        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        .accessDeniedHandler((HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) -> {
          res.setStatus(HttpStatus.FORBIDDEN.value());
          res.setContentType("application/json");
          res.getWriter().write("{\"error\":\"forbidden\"}");
        })
      )
      .authorizeHttpRequests(authz -> authz
        .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/register").permitAll()
        .requestMatchers("/api/auth/me").authenticated()
        .anyRequest().permitAll()
      )
      .securityContext(context -> context.securityContextRepository(securityContextRepository()));

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // keep in sync with test data
  }

  @Bean
  public AuthenticationManager authenticationManager(CustomAuthenticationProvider customAuthenticationProvider) {
    return new ProviderManager(customAuthenticationProvider);
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }
}
