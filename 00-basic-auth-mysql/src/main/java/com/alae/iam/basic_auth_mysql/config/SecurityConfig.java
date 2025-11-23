package com.alae.iam.basic_auth_mysql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.alae.iam.basic_auth_mysql.domain.Authority;
import com.alae.iam.basic_auth_mysql.repository.UserRepository;

@Configuration
public class SecurityConfig {

  private final CustomBasicAuthenticationEntryPoint customBasicAuthenticationEntryPoint;

  public SecurityConfig(CustomBasicAuthenticationEntryPoint customBasicAuthenticationEntryPoint) {
    this.customBasicAuthenticationEntryPoint = customBasicAuthenticationEntryPoint;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
          .requestMatchers("/public/**").permitAll()
          .requestMatchers(HttpMethod.POST, "/register").permitAll()
          .requestMatchers("/home").authenticated()
          .anyRequest().denyAll()
      )
      .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(customBasicAuthenticationEntryPoint));
    return http.build();
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return username -> users.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
