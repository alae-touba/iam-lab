# plan to implement the project

---

# Implementation Plan for CLI Agent

## 0) Project scaffold (already from start.spring.io)

**Inputs** (as used on start.spring.io):

* Group: `com.alae.iam`
* Artifact: `basic-auth-mysql`
* Name: `Basic Auth with MySQL`
* Dependencies: `Spring Web`, `Spring Security`, `Spring Data JPA`, `Validation`, `MySQL Driver`, `Lombok`, `Flyway` (optional but recommended), `DevTools` (optional)

> MySQL is **already running locally**, so you can skip container startup instructions and just connect to it.

---

## 1) Create configuration

**File:** `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/my_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: validate   # set to 'update' only if you skip Flyway
    open-in-view: false
    properties:
      hibernate.format_sql: true
  flyway:
    enabled: true

server:
  port: 8080

logging:
  level:
    org.springframework.security: INFO
```

---

## 2) Database schema (Flyway)

**File:** `src/main/resources/db/migration/V1__init.sql`

```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE authorities (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE users_authorities (
  user_id BIGINT NOT NULL,
  authority_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, authority_id),
  CONSTRAINT fk_users_authorities_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_users_authorities_authority FOREIGN KEY (authority_id) REFERENCES authorities(id)
);

-- seed a default ROLE_USER
INSERT INTO authorities(name) VALUES ('ROLE_USER');
```

---

## 3) Domain & persistence

**User.java**

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String username;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(nullable = false, length = 255)
  private String password;

  @Column(nullable = false)
  private boolean enabled = true;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "users_authorities",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "authority_id")
  )
  private Set<Authority> authorities = new HashSet<>();
}
```

**Authority.java**

```java
@Entity
@Table(name = "authorities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Authority {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String name; // e.g. ROLE_USER
}
```

**UserRepository.java**

```java
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);
  boolean existsByUsername(String username);
  boolean existsByEmail(String email);
}
```

**AuthorityRepository.java**

```java
public interface AuthorityRepository extends JpaRepository<Authority, Long> {
  Optional<Authority> findByName(String name);
}
```

---

## 4) Security config

**SecurityConfig.java**

```java
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
          .requestMatchers("/public/**").permitAll()
          .requestMatchers("/register").permitAll()
          .requestMatchers("/home").authenticated()
          .anyRequest().denyAll()
      )
      .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return username -> users.findByUsername(username)
        .map(u -> org.springframework.security.core.userdetails.User
            .withUsername(u.getUsername())
            .password(u.getPassword())
            .authorities(u.getAuthorities().stream().map(Authority::getName).toArray(String[]::new))
            .disabled(!u.isEnabled())
            .build())
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
```

---

## 5) Controllers & DTOs

**RegisterRequest.java**

```java
public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 100) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 100) String password
) {}
```

**AppController.java**

```java
@RestController
public class AppController {
  @GetMapping("/public")
  public String publicEndpoint() {
    return "This is public";
  }

  @GetMapping("/home")
  public String home(Authentication auth) {
    return "Welcome, " + auth.getName();
  }
}
```

**AuthController.java**

```java
@RestController
@RequiredArgsConstructor
public class AuthController {

  private final UserRepository userRepository;
  private final AuthorityRepository authorityRepository;
  private final PasswordEncoder passwordEncoder;

  @PostMapping("/register")
  public RegisterResponse register(@RequestBody @Valid RegisterRequest req) {
    if (userRepository.existsByUsername(req.username())) {
      throw new IllegalArgumentException("Username already taken");
    }
    if (userRepository.existsByEmail(req.email())) {
      throw new IllegalArgumentException("Email already taken");
    }

    Authority roleUser = authorityRepository.findByName("ROLE_USER")
        .orElseGet(() -> authorityRepository.save(Authority.builder().name("ROLE_USER").build()));

    User user = User.builder()
        .username(req.username())
        .email(req.email())
        .password(passwordEncoder.encode(req.password()))
        .enabled(true)
        .build();
    user.getAuthorities().add(roleUser);

    User saved = userRepository.save(user);
    return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail());
  }
}
```

**RegisterResponse.java**

```java
public record RegisterResponse(Long id, String username, String email) {}
```

---

## 6) Error handling

**GlobalExceptionHandler.java**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
    var msg = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .findFirst()
        .orElse("Validation error");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
  }
}
```

---

## 7) Run & verify

**Run app (MySQL already running locally):**

```bash
./mvnw spring-boot:run
```

**Test endpoints:**

```bash
# Public
curl -i http://localhost:8080/public

# Register user
curl -i -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alae","email":"alae@example.com","password":"StrongPass123"}'

# Auth required
curl -i http://localhost:8080/home

# With Basic Auth
curl -i -u alae:StrongPass123 http://localhost:8080/home
```

---

## 8) Repository layout

```
iam-lab/
  README.md
  basic-auth-mysql/
    README.md
    pom.xml
    src/main/java/...
    src/main/resources/application.yml
    src/main/resources/db/migration/V1__init.sql
```

**basic-auth-mysql/README.md**

```md
# Basic Auth with MySQL

Spring Security app with:
- `/public` (public)
- `/register` (public POST for user creation)
- `/home` (requires Basic Auth)

## Run
- Ensure MySQL is already running locally on port 3306 with db `my_db`
- `./mvnw spring-boot:run`

## Test
Use the curl commands above.
```

---

## 9) Notes / best practices

* Always hash passwords (BCrypt).
* Use `ROLE_` authorities for simple RBAC.
* Keep stateless for APIs; enable CSRF only if using cookies.
* Use Flyway for schema migrations.
* Consider Spring Actuator for monitoring.

