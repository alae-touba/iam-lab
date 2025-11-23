# Spring Boot Basic Authentication with MySQL

This project is a simple Spring Boot application that demonstrates how to implement basic authentication using Spring Security and persist user data in a MySQL database.

## Technologies Used

*   Java 17
*   Spring Boot 3
*   Spring Security
*   Spring Data JPA
*   Hibernate
*   MySQL
*   Lombok
*   Maven

## Prerequisites

*   JDK 17 or later
*   Maven 3.6 or later
*   A running MySQL database instance

## Configuration

To run this application, you need to configure the database connection in the `src/main/resources/application.yml` file. Update the `spring.datasource` properties with your MySQL database details.

**application.yml template:**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database_name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: your_username
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update # Creates/updates the database schema automatically
    open-in-view: false
    properties:
      hibernate.format_sql: true

server:
  port: 8080

logging:
  level:
    org.springframework.security: INFO
```

## How to Run

1.  Clone the repository.
2.  Configure the database connection in `src/main/resources/application.yml` as described above.
3.  Open a terminal in the project root directory.
4.  Run the application using Maven:

    ```bash
    ./mvnw spring-boot:run
    ```

5.  The application will start on port 8080.

## API Endpoints

The application provides the following endpoints:

*   `POST /register`: Register a new user. The request body should be a JSON object with `username`, `email`, and `password`.

    **Request Body Example:**
    ```json
    {
        "username": "testuser",
        "email": "test@example.com",
        "password": "password123"
    }
    ```

*   `GET /public`: A public endpoint that can be accessed by anyone without authentication.

*   `GET /home`: A protected endpoint that requires authentication. It returns a welcome message with the authenticated user's name.

    To access this endpoint, you need to provide the username and password of a registered user using Basic Authentication.

## Testing

This project uses Testcontainers for integration testing, so you don't need a separate running MySQL instance to run the tests. The tests will automatically spin up a MySQL container.

To run the tests, execute the following command in the project root directory:

```bash
./mvnw.cmd test
```




## How Authentication Works (HTTP Basic + Spring Security)

https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/basic.html

This project uses **HTTP Basic** with a **stateless** security setup.

### High-level flow

1. **Incoming request** hits the `SecurityFilterChain` (configured in `SecurityConfig`).
2. **BasicAuthenticationFilter** checks for an `Authorization` header:

   ```
   Authorization: Basic base64(username:password)
   ```
3. If present, it creates a **`UsernamePasswordAuthenticationToken`** (unauthenticated) with the extracted username/password and delegates to the **`AuthenticationManager`**.
4. The default **`AuthenticationManager`** is a **`ProviderManager`**, which iterates over configured **`AuthenticationProvider`s**. We use **`DaoAuthenticationProvider`**.
5. **`DaoAuthenticationProvider`** calls:

   * **`UserDetailsService#loadUserByUsername`** → loads a `UserDetails` from the database (our custom `UserDetailsService` backed by JPA via `UserRepository`).
   * **`PasswordEncoder#matches`** (BCrypt) → compares the raw password from the request with the hashed password in the DB.
6. If credentials are valid, the provider returns an **authenticated** `UsernamePasswordAuthenticationToken`, whose **principal** is the `UserDetails` returned by the `UserDetailsService`.
7. **BasicAuthenticationFilter** then stores the authenticated `Authentication` in the **`SecurityContext`** via `SecurityContextHolder`.
8. Because this app is **stateless** (`SessionCreationPolicy.STATELESS`), Spring **does not create** or persist an HTTP session (`JSESSIONID`)—each protected request must include the Basic header again.

### What happens on errors

* **No/invalid credentials** for a protected endpoint (e.g. `GET /home`) → **401 Unauthorized**.
  Spring triggers the **`AuthenticationEntryPoint`** for Basic (sends `WWW-Authenticate: Basic`).
* **Authenticated but forbidden** (not used here, but if you add role checks and the user lacks them) → **403 Forbidden** via `AccessDeniedHandler`.

### Components used (and where)

* **`SecurityFilterChain`** – defined in `SecurityConfig`.
  Sets:

  * `httpBasic()` → enables **`BasicAuthenticationFilter`**
  * `authorizeHttpRequests(...)` → `/public` and `/register` are public; `/home` requires auth
  * `sessionManagement().sessionCreationPolicy(STATELESS)` → no sessions/cookies
  * `csrf().disable()` → OK for stateless APIs (no browser form login/cookies)
* **`BasicAuthenticationFilter`** – reads `Authorization: Basic ...`, builds `UsernamePasswordAuthenticationToken`, delegates to `AuthenticationManager`.
* **`AuthenticationManager` (ProviderManager)** – dispatches to providers.
* **`DaoAuthenticationProvider`** – authenticates username+password using:

  * **`UserDetailsService`** – loads user by username from DB (via `UserRepository`).
  * **`PasswordEncoder` (BCrypt)** – verifies password.
* **`UserDetails`** – built from our JPA `User` entity (username, password hash, authorities).
* **`SecurityContextHolder`** – holds the authenticated `Authentication` for the request.

### Request/response examples

* **Public endpoint** (no auth required):

  ```bash
  curl -i http://localhost:8080/public
  ```

  → `200 OK`, body: `This is public`

* **Register** (no auth required, validates payload, creates DB user with BCrypt password):

  ```bash
  curl -i -X POST http://localhost:8080/register \
    -H "Content-Type: application/json" \
    -d '{"username":"alae","email":"alae@example.com","password":"StrongPass123"}'
  ```

  → `201 Created` with user summary JSON (no password).

* **Access protected endpoint without auth**:

  ```bash
  curl -i http://localhost:8080/home
  ```

  → `401 Unauthorized` + `WWW-Authenticate: Basic realm="Realm"`

* **Access protected endpoint with Basic Auth**:

  ```bash
  curl -i -u alae:StrongPass123 http://localhost:8080/home
  ```

  → `200 OK`, body: `Welcome, alae`
  → No `Set-Cookie` header (stateless).

### Why stateless?

* Basic Auth is naturally stateless; every request carries credentials.
* No session storage or CSRF tokens are needed for this API style.
* Tests assert no `Set-Cookie` header is returned.

### Class/Interface cheat sheet

| Purpose                | Class/Interface                             | Where it’s wired                                |
| ---------------------- | ------------------------------------------- | ----------------------------------------------- |
| Security rules & Basic | `SecurityFilterChain`                       | `SecurityConfig.securityFilterChain(...)`       |
| Authentication filter  | `BasicAuthenticationFilter`                 | Auto-enabled by `.httpBasic()`                  |
| Authentication manager | `AuthenticationManager` (`ProviderManager`) | Auto-configured by Spring                       |
| Provider for user/pass | `DaoAuthenticationProvider`                 | Auto-configured; uses our beans                 |
| Load users from DB     | `UserDetailsService`                        | Bean in `SecurityConfig` using `UserRepository` |
| Password hashing       | `PasswordEncoder` (BCrypt)                  | Bean in `SecurityConfig`                        |
| Principal model        | `UserDetails`                               | Built from our JPA `User` + authorities         |
| Security context       | `SecurityContextHolder`                     | Populated by `BasicAuthenticationFilter`        |

> TL;DR: **BasicAuthenticationFilter** → builds `UsernamePasswordAuthenticationToken` from the `Authorization` header → **AuthenticationManager (ProviderManager)** → **DaoAuthenticationProvider** → **UserDetailsService + PasswordEncoder** → on success, authenticated token goes into **SecurityContextHolder**; app is **stateless**, so no session cookie is issued.
