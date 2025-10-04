# Spring Boot Manual Authentication with MySQL

This project is a Spring Boot application that demonstrates how to implement a custom, manual authentication flow using Spring Security. It avoids high-level DSLs like `.formLogin()` or `.httpBasic()` to show how the underlying components work together. User data is persisted in a MySQL database.

## Features

*   User registration with password hashing (BCrypt).
*   Manual login processing with a custom `AuthenticationProvider`.
*   Session-based (stateful) authentication.
*   Custom exception handling for authentication failures.
*   REST endpoints for registration, login, and accessing protected resources.

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
*   A running MySQL database instance.

## Configuration

Configure the database connection in `src/main/resources/application.yml`. Update the `spring.datasource` properties with your MySQL details.

**application.yml template:**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database_name?useSSL=false
    username: your_username
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate.format_sql: true

server:
  port: 8080

logging:
  level:
    org.springframework.security: DEBUG
```

## How to Run

1.  Clone the repository.
2.  Create the database in your MySQL instance.
3.  Configure the database connection in `src/main/resources/application.yml`.
4.  Run the application using Maven:
    ```bash
    ./mvnw spring-boot:run
    ```
5.  The application will start on port 8080.

## API Endpoints

| Method | Path                  | Description                                    | Request Body                                      | Success Response                         |
| :----- | :-------------------- | :--------------------------------------------- | :------------------------------------------------ | :--------------------------------------- |
| `POST` | `/api/auth/register`  | Registers a new user.                          | `application/json` with `username`, `email`, `password`. | `201 CREATED` with user details.         |
| `POST` | `/api/auth/login`     | Authenticates a user manually.                 | `application/json` with `usernameOrEmail`, `password`. | `200 OK` with user details.              |
| `GET`  | `/api/auth/me`        | Retrieves the authenticated user's details.    | (None)                                            | `200 OK` with user details.              |
| `GET`  | `/api/secure/hello`   | A protected endpoint that requires authentication. | (None)                                            | `200 OK` with a welcome message.         |
| `POST` | `/api/auth/logout`    | Logs out the user and invalidates the session.    | (None)                                            | `204 NO CONTENT`.                        |

---

## How Authentication Works: A Deep Dive into Manual Implementation

This project demonstrates a manual authentication flow, providing deep insight into Spring Security's core components without relying on helpers like `.formLogin()` or `.httpBasic()`. The key is the **`CustomAuthenticationProvider`**, which we explicitly wire into the security configuration.

The application is **stateful**. After a successful login, the server creates an `HttpSession` and sends a `JSESSIONID` cookie to the client. This cookie is then used to identify the user in subsequent requests, maintaining the authenticated session.

### The Authentication Flow

1.  **Client Sends Login Request:**
    *   The user sends a `POST` request to `/api/auth/login` with a JSON body containing `usernameOrEmail` and `password`.

2.  **`AuthController` Receives Credentials:**
    *   Unlike flows that use filters to intercept credentials (e.g., `UsernamePasswordAuthenticationFilter`), our `AuthController` explicitly receives the `LoginRequest` DTO.

3.  **Delegation to `AuthenticationManager`:**
    *   The controller creates an unauthenticated `UsernamePasswordAuthenticationToken` from the request data.
    *   It then calls `authenticationManager.authenticate(token)`. The `AuthenticationManager` (a `ProviderManager`) is injected into the controller.

4.  **`ProviderManager` Finds Our Custom Provider:**
    *   The `ProviderManager` iterates through its list of configured `AuthenticationProvider`s.
    *   It finds our `CustomAuthenticationProvider` because it's declared to support `UsernamePasswordAuthenticationToken`.

5.  **`CustomAuthenticationProvider` Logic:**
    *   This is the core of our manual flow. The `authenticate` method is executed.
    *   It retrieves the user from the database via `AppUserRepository`. If the user is not found, it throws an `InvalidCredentialsException`.
    *   It uses the `PasswordEncoder` to compare the raw password from the token with the hashed password from the database. If they don't match, it throws an `InvalidCredentialsException`.
    *   It checks user account status (e.g., locked, disabled) and throws appropriate exceptions.

6.  **Successful Authentication:**
    *   If all checks pass, the provider creates a **new, authenticated** `UsernamePasswordAuthenticationToken`. This token contains the `AuthPrincipal` (our custom `UserDetails` object), an empty password (`null`), and a list of granted authorities.
    *   This authenticated token is returned to the `ProviderManager`.

7.  **Updating the `SecurityContext` and Creating a Session:**
    *   The `ProviderManager` returns the authenticated token to the `AuthController`.
    *   The controller manually places this `Authentication` object into the `SecurityContextHolder`: `SecurityContextHolder.getContext().setAuthentication(authentication)`.
    *   This action signals to Spring Security that the user is now authenticated. The `SecurityContextPersistenceFilter` (enabled by default) detects the populated `SecurityContext` and saves it into the `HttpSession`.
    *   The server then generates a `JSESSIONID` and sends it back to the client in a `Set-Cookie` header.

8.  **Returning the Response:**
    *   The controller returns a `200 OK` response with the user's details, along with the `Set-Cookie` header.

### Accessing Protected Endpoints

*   Once authenticated, the client includes the `JSESSIONID` cookie in all subsequent requests to the server.
*   Spring Security's `SecurityContextPersistenceFilter` intercepts the request, reads the cookie, retrieves the corresponding `HttpSession`, and repopulates the `SecurityContextHolder` with the `Authentication` object found in the session.
*   Because the `SecurityContextHolder` is now populated, the request is recognized as authenticated and is allowed to proceed to the protected endpoint (e.g., `/api/secure/hello`).

### Logout

*   When the client sends a `POST` request to `/api/auth/logout`, the `SecurityContextLogoutHandler` (configured by default) invalidates the `HttpSession`, clears the `SecurityContextHolder`, and instructs the client to delete the `JSESSIONID` cookie.

### Key Components and Their Roles

| Purpose                     | Class/Interface                             | Where it’s Wired                                                              |
| --------------------------- | ------------------------------------------- | ----------------------------------------------------------------------------- |
| Security rules & wiring     | `SecurityFilterChain`                       | `SecurityConfig.securityFilterChain(...)`                                     |
| Custom authentication logic | `CustomAuthenticationProvider`              | Injected into the `AuthenticationManager` bean in `SecurityConfig`.           |
| Authentication manager      | `AuthenticationManager` (`ProviderManager`) | Configured as a bean in `SecurityConfig` with our custom provider.            |
| Load users from DB          | `AppUserRepository` (JPA)                   | Injected into `CustomAuthenticationProvider`.                                 |
| Password hashing            | `PasswordEncoder` (BCrypt)                  | Bean in `SecurityConfig`, injected into `CustomAuthenticationProvider`.       |
| Principal model             | `AuthPrincipal`                             | Custom `UserDetails` implementation, built from our JPA `AppUser` entity.     |
| Security context            | `SecurityContextHolder`                     | Manually populated in `AuthController` after successful authentication.       |
| Custom error handling       | `CustomAuthenticationEntryPoint`            | Handles `401 Unauthorized` for unauthenticated access to protected resources. |
| Custom error handling       | `CustomAccessDeniedHandler`                 | Handles `403 Forbidden` for unauthorized access (e.g., role mismatch).        |

### Request/Response Examples (cURL)

*(Note: The following commands use a temporary file `cookies.txt` to store and send the session cookie.)*

*   **Register a new user:**
    ```bash
    curl -i -X POST http://localhost:8080/api/auth/register \
      -H "Content-Type: application/json" \
      -d '{"username":"testuser","email":"test@example.com","password":"password123"}'
    ```
    → `201 Created` with user details.

*   **Log in and capture the session cookie:**
    ```bash
    curl -i -X POST http://localhost:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"usernameOrEmail":"testuser","password":"password123"}' \
      -c cookies.txt
    ```
    → `200 OK` with user details and a `Set-Cookie: JSESSIONID=...` header.

*   **Access a protected endpoint with the session cookie:**
    ```bash
    curl -i http://localhost:8080/api/secure/hello -b cookies.txt
    ```
    → `200 OK` with the welcome message.

*   **Get current user details:**
    ```bash
    curl -i http://localhost:8080/api/auth/me -b cookies.txt
    ```
    → `200 OK` with user details.

*   **Log out and invalidate the session:**
    ```bash
    curl -i -X POST http://localhost:8080/api/auth/logout -b cookies.txt
    ```
    → `204 No Content`.

> TL;DR: The `AuthController` receives credentials, creates a token, and sends it to the `AuthenticationManager`. Our `CustomAuthenticationProvider` validates the credentials. On success, the controller places the authenticated token in the `SecurityContextHolder`, which causes Spring to create an `HttpSession` and issue a `JSESSIONID` cookie for stateful, session-based authentication.
