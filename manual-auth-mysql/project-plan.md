# Manual Auth (Custom AuthenticationProvider) — Implementation Plan (Agent-Ready)

**Goal:** Build a Spring Boot 3.3+ app that authenticates users *programmatically* using a **custom `AuthenticationProvider`**, with sessions (no form login, no HTTP Basic). Users are in **MySQL**. **No Flyway**, **no docker-compose**, **no Postman/Bruno**, **no .env**. Integration tests use **Testcontainers**.

---


## 2) Package layout

```
src/main/java/com/alae/iamlab/
  ManualAuthMysqlApplication.java
  config/
    SecurityConfig.java
  auth/
    AuthPrincipal.java
    CustomAuthenticationProvider.java
  controller/
    AuthController.java
  domain/
    AppUser.java
  dto/
    LoginRequest.java
    LoginResponse.java
    ErrorResponse.java
  exception/
    ApiExceptionHandler.java
  repository/
    AppUserRepository.java
  service/
    AuthService.java
```

> Use **exact** package names: `config`, `auth`, `controller`, `domain`, `dto`, `exception`, `repository`, `service`.

---

## 3) Configuration

### 3.1 `src/main/resources/application.yaml`

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/manual_auth_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: update   # no Flyway; let JPA create/update schema for dev
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.springframework.security: INFO
```

### 3.2 Local MySQL (manual)

Create DB & user locally (adjust as needed):

```sql
CREATE DATABASE IF NOT EXISTS manual_auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON manual_auth_db.* TO 'root'@'%';
FLUSH PRIVILEGES;
```

---

## 4) Domain + Repository

### 4.1 `domain/AppUser.java`

```java
package com.alae.iamlab.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String username;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "account_non_locked", nullable = false)
  private boolean accountNonLocked = true;

  @Column(name = "roles_csv", length = 255)
  private String rolesCsv; // e.g., "ROLE_USER,ROLE_ADMIN"

  // getters/setters
  // ...
}
```

### 4.2 `repository/AppUserRepository.java`

```java
package com.alae.iamlab.repository;

import com.alae.iamlab.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsername(String username);
  Optional<AppUser> findByEmail(String email);
}
```

---

## 5) Auth model

### 5.1 `auth/AuthPrincipal.java`

```java
package com.alae.iamlab.auth;

import java.util.List;

public record AuthPrincipal(Long id, String username, String email, List<String> roles) {}
```

### 5.2 `auth/CustomAuthenticationProvider.java`

```java
package com.alae.iamlab.auth;

import com.alae.iamlab.domain.AppUser;
import com.alae.iamlab.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

  private final AppUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    final String usernameOrEmail = authentication.getName();
    final String rawPassword = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();

    AppUser user = userRepository.findByUsername(usernameOrEmail)
        .or(() -> userRepository.findByEmail(usernameOrEmail))
        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

    if (!user.isEnabled()) throw new DisabledException("Account disabled");
    if (!user.isAccountNonLocked()) throw new LockedException("Account locked");

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid credentials");
    }

    List<String> roles = splitCsv(user.getRolesCsv());
    var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
    var principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getEmail(), roles);

    // never return the raw password
    return new UsernamePasswordAuthenticationToken(principal, null, authorities);
  }

  private static List<String> splitCsv(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
```

---

## 6) Security configuration

### 6.1 `config/SecurityConfig.java`

```java
package com.alae.iamlab.config;

import com.alae.iamlab.auth.CustomAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final CustomAuthenticationProvider customAuthenticationProvider;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .formLogin(AbstractHttpConfigurer::disable)
      .httpBasic(AbstractHttpConfigurer::disable)
      .csrf(AbstractHttpConfigurer::disable) // learning mode (enable later)

      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

      .exceptionHandling(e -> e
        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        .accessDeniedHandler((req, res, ex) -> {
          res.setStatus(HttpStatus.FORBIDDEN.value());
          res.setContentType("application/json");
          res.getWriter().write("{\"error\":\"forbidden\"}");
        })
      )

      .authorizeHttpRequests(authz -> authz
        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
        .requestMatchers("/api/auth/me").authenticated()
        .anyRequest().permitAll()
      );

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // keep in sync with test data
  }

  @Bean
  public AuthenticationManager authenticationManager() {
    return new ProviderManager(customAuthenticationProvider);
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }
}
```

---

## 7) DTOs

### 7.1 `dto/LoginRequest.java`

```java
package com.alae.iamlab.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String usernameOrEmail,
    @NotBlank String password
) {}
```

### 7.2 `dto/LoginResponse.java`

```java
package com.alae.iamlab.dto;

import java.util.List;

public record LoginResponse(Long id, String username, String email, List<String> roles) {}
```

### 7.3 `dto/ErrorResponse.java`

```java
package com.alae.iamlab.dto;

public record ErrorResponse(String error) {}
```

---

## 8) Service (login helper)

### 8.1 `service/AuthService.java`

```java
package com.alae.iamlab.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final SecurityContextRepository securityContextRepository;

  public void completeLogin(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    // DEV: keep simple; production should rotate session id
    // request.changeSessionId();

    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(authentication);
    SecurityContextHolder.setContext(ctx);
    securityContextRepository.saveContext(ctx, request, response);
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
  }
}
```

---

## 9) Controller

### 9.1 `controller/AuthController.java`

```java
package com.alae.iamlab.controller;

import com.alae.iamlab.auth.AuthPrincipal;
import com.alae.iamlab.dto.ErrorResponse;
import com.alae.iamlab.dto.LoginRequest;
import com.alae.iamlab.dto.LoginResponse;
import com.alae.iamlab.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest req,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
    Authentication token = new UsernamePasswordAuthenticationToken(req.usernameOrEmail(), req.password());
    try {
      Authentication auth = authenticationManager.authenticate(token);
      authService.completeLogin(auth, request, response);

      AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
      return ResponseEntity.ok(new LoginResponse(p.id(), p.username(), p.email(), p.roles()));

    } catch (BadCredentialsException e) {
      return ResponseEntity.status(401).body(new ErrorResponse("invalid_credentials"));
    } catch (LockedException e) {
      return ResponseEntity.status(423).body(new ErrorResponse("account_locked"));
    } catch (DisabledException e) {
      return ResponseEntity.status(403).body(new ErrorResponse("account_disabled"));
    }
  }

  @GetMapping("/me")
  public ResponseEntity<?> me() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return ResponseEntity.status(401).body(new ErrorResponse("not_authenticated"));
    }
    AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
    return ResponseEntity.ok(new LoginResponse(p.id(), p.username(), p.email(), p.roles()));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(request, response);
    return ResponseEntity.ok().build();
  }
}
```

---

## 10) Exception handling

### 10.1 `exception/ApiExceptionHandler.java`

```java
package com.alae.iamlab.exception;

import com.alae.iamlab.dto.ErrorResponse;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("validation_error"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("server_error"));
  }
}
```

---

## 11) Seeding data (dev-only snippet)

Without Flyway, create users one time (e.g., via a `CommandLineRunner` or manual SQL). Example **runner** (optional):

```java
// In config or service package, dev-only
@Bean
CommandLineRunner seed(AppUserRepository repo, PasswordEncoder enc) {
  return args -> {
    if (repo.count() == 0) {
      var u = new AppUser();
      u.setUsername("alice");
      u.setEmail("alice@example.com");
      u.setPasswordHash(enc.encode("secret"));
      u.setRolesCsv("ROLE_USER");
      repo.save(u);

      var admin = new AppUser();
      admin.setUsername("admin");
      admin.setEmail("admin@example.com");
      admin.setPasswordHash(enc.encode("secret"));
      admin.setRolesCsv("ROLE_ADMIN,ROLE_USER");
      repo.save(admin);
    }
  };
}
```

> Remove this seeding bean once you’re done testing.

---

## 12) Integration tests (Testcontainers)

### 12.1 `src/test/java/com/alae/iamlab/AuthFlowIT.java`

```java
package com.alae.iamlab;

import com.alae.iamlab.domain.AppUser;
import com.alae.iamlab.repository.AppUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthFlowIT {

  @Container
  static MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.4.0")
          .withDatabaseName("manual_auth_db")
          .withUsername("root")
          .withPassword("root");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop"); // test isolation
  }

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository repo;
  @Autowired PasswordEncoder encoder;

  @BeforeEach
  void seed() {
    repo.deleteAll();

    AppUser u = new AppUser();
    u.setUsername("alice");
    u.setEmail("alice@example.com");
    u.setPasswordHash(encoder.encode("secret"));
    u.setRolesCsv("ROLE_USER");
    repo.save(u);
  }

  @Test
  void login_me_logout_happy_path() throws Exception {
    // 1) login
    var loginRes = mvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content("""
                 {"usernameOrEmail":"alice","password":"secret"}
                 """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andReturn();

    // extract session cookie
    var cookies = loginRes.getResponse().getCookies();

    // 2) me
    mvc.perform(get("/api/auth/me").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alice@example.com"));

    // 3) logout
    mvc.perform(post("/api/auth/logout").cookie(cookies))
        .andExpect(status().isOk());

    // 4) me after logout -> 401
    mvc.perform(get("/api/auth/me").cookie(cookies))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_wrong_password_returns_401() throws Exception {
    mvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content("""
                 {"usernameOrEmail":"alice","password":"wrong"}
                 """))
        .andExpect(status().isUnauthorized());
  }
}
```

---

## 13) Application entrypoint

### 13.1 `ManualAuthMysqlApplication.java`

```java
package com.alae.iamlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ManualAuthMysqlApplication {
  public static void main(String[] args) {
    SpringApplication.run(ManualAuthMysqlApplication.class, args);
  }
}
```

---

## 14) Acceptance criteria

* [ ] Custom `AuthenticationProvider` used (no `UserDetailsService`, no `DaoAuthenticationProvider`).
* [ ] `POST /api/auth/login` authenticates and persists session.
* [ ] `GET /api/auth/me` returns `{ id, username, email, roles }` when authenticated, `401` otherwise.
* [ ] `POST /api/auth/logout` invalidates session.
* [ ] `application.yaml` holds DB url/username/password; DB name is `manual_auth_db`.
* [ ] No Flyway, no docker-compose, no Postman/Bruno instructions, no .env.
* [ ] Integration tests pass with Testcontainers (happy path + invalid password).

---

## 15) Notes (security hardening later)

* CSRF is **disabled for learning**; enable later for session-based apps.
* Add session fixation protection later: `request.changeSessionId()` right after successful authentication.
* Add account lockout on repeated failures; add password-upgrade policy (rehash when cost changes); consider MFA.

---

### Handy snippets (for inspiration)

**BCrypt encode on the fly (e.g., in a REPL or a runner):**

```java
new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("secret");
```

**Rotate session id (production):**

```java
// after authenticate(token)
request.changeSessionId();
```

**Return JSON 401 instead of redirect (already in config):**

```java
.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
```

**Build authorities from CSV:**

```java
var authorities = java.util.Arrays.stream(csv.split(","))
  .map(String::trim)
  .filter(s -> !s.isBlank())
  .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
  .toList();
```

---

That’s everything the coding agent needs to implement `manual-auth-mysql` end-to-end with your preferred package structure, DB name, and testing approach.
