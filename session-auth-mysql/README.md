# Session Auth MySQL

This project is a Spring Boot application that demonstrates session-based authentication using Spring Security, with MySQL as the data store.

## Features

-   User registration
-   Username/password authentication with session cookies (JSESSIONID)
-   Password encoding using BCrypt
-   REST endpoints for authentication and accessing protected resources


## Prerequisites

Before you begin, ensure you have the following installed:

-   Java 17 or higher
-   Maven
-   MySQL Server

## Getting Started

### 1. Database Setup

You need to create the database in MySQL before running the application. Execute the following SQL command:

```sql
CREATE DATABASE IF NOT EXISTS session_auth_db;
```

The necessary tables (`users`, `authorities`, `user_authorities`) will be created automatically by Hibernate when the application starts for the first time, as `spring.jpa.hibernate.ddl-auto` is set to `update`.

### 2. Running the Application

You can run the application using the Maven wrapper:

```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

## API Endpoints

Here is a summary of the available REST endpoints:

| Method | Path                  | Description                                       | Request Body                                                                 | Success Response                                     |
| :----- | :-------------------- | :------------------------------------------------ | :--------------------------------------------------------------------------- | :--------------------------------------------------- |
| `POST` | `/api/auth/register`  | Registers a new user.                             | `application/json` with `username`, `email`, `password`.                     | `201 CREATED` with user summary.                     |
| `POST` | `/api/auth/login`     | Authenticates a user and creates a session.       | `application/x-www-form-urlencoded` with `usernameOrEmail` and `password`.   | `200 OK` with user summary and `JSESSIONID` cookie.  |
| `POST` | `/api/auth/logout`    | Logs out the user and invalidates the session.    | (None)                                                                       | `204 NO CONTENT`.                                    |
| `GET`  | `/api/auth/me`        | Retrieves the details of the authenticated user.  | (None)                                                                       | `200 OK` with user summary.                          |
| `GET`  | `/api/secure/ping`    | A protected endpoint to check authentication.     | (None)                                                                       | `200 OK` with `{"pong": true}`.                      |

---

## How Authentication Works (Form Login + Server Sessions)

https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html

This project relies on Spring Security's form-login flow with server-managed sessions. Authentication state lives in the `HttpSession` and is tracked on the client by the `JSESSIONID` cookie.

### High-level flow

1. The incoming request goes through the `SecurityFilterChain` configured in `SecurityConfig`.
2. `POST /api/auth/login` is handled by `UsernamePasswordAuthenticationFilter` (enabled via `.formLogin()`), which reads `usernameOrEmail` and `password` from the form body.
3. The filter builds an unauthenticated `UsernamePasswordAuthenticationToken` and delegates to the `AuthenticationManager` (`ProviderManager`).
4. `DaoAuthenticationProvider` calls `CustomUserDetailsService#loadUserByUsername` to fetch the user (by username **or** email) and checks the password with `BCryptPasswordEncoder`.
5. On success, the provider returns an authenticated token that is stored in the `SecurityContextHolder`. A session is created when needed (`SessionCreationPolicy.IF_REQUIRED`).
6. `RestAuthenticationSuccessHandler` serializes the `UserSummary` response, and the servlet container issues a `JSESSIONID` cookie that points to the server-side session.
7. Subsequent requests send the `JSESSIONID`; Spring restores the `Authentication` from the session before hitting controllers, so protected endpoints see the user as logged in.

### Session lifecycle

- `SessionCreationPolicy.IF_REQUIRED` keeps things efficient: only login creates a session, and stateless reads stay session-free.
- Session data holds the `SecurityContext`; losing the cookie or expiring the session forces a fresh login.
- `/api/auth/logout` invalidates the `HttpSession` and instructs the client to remove `JSESSIONID`.

### What happens on errors

- Bad credentials on `/api/auth/login` trigger `RestAuthenticationFailureHandler`, which returns `401` with a JSON error body.
- Unauthenticated access to secure endpoints calls `RestAuthenticationEntryPoint`, returning `401` with a JSON message.
- If you later add role checks, access denials will surface as `403` from Spring's `AccessDeniedHandler`.

### Components used (and where)

- `SecurityFilterChain` — `SecurityConfig.filterChain(...)`
- `formLogin()` — customizes the login URL, parameters, and success/failure handlers
- `RestAuthenticationSuccessHandler` — writes the JSON body after login success
- `RestAuthenticationFailureHandler` — uniform JSON for failed login attempts
- `RestAuthenticationEntryPoint` — guards protected endpoints when no session is present
- `CustomUserDetailsService` — loads users from MySQL via `UserRepository`
- `PasswordEncoder` (`BCrypt`) — hashes and verifies credentials

### Request/response snapshots

- **Login and capture the session cookie**

  ```bash
  curl -i -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "usernameOrEmail=newuser&password=password123" \
    -c cookies.txt
  ```

  The response includes `Set-Cookie: JSESSIONID=...; Path=/; HttpOnly`.

- **Call a protected endpoint with the session**

  ```bash
  curl -i -X GET http://localhost:8080/api/secure/ping \
    -b cookies.txt
  ```

- **Log out and invalidate the session**

  ```bash
  curl -i -X POST http://localhost:8080/api/auth/logout \
    -b cookies.txt
  ```

### Why sessions here?

- SPA/REST clients can authenticate once and reuse the `JSESSIONID` without resending the password.
- Session invalidation on logout and cookie deletion keep credentials off the wire after login.
- Stateful sessions simplify CSRF protection if you later add browser flows (enable CSRF accordingly).

### Usage Examples (cURL)

You can use the provided `.sh` or `.bat` scripts in the `/scripts` directory, or use the `curl` commands below.

*(Note: The following commands use a temporary file `cookies.txt` to store the session cookie.)*

#### Register a new user

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"username": "newuser", "email": "new@example.com", "password": "password123"}' \
  http://localhost:8080/api/auth/register
```

#### Login

```bash
curl -i -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "usernameOrEmail=newuser&password=password123" \
  -c cookies.txt \
  http://localhost:8080/api/auth/login
```

#### Get current user details

```bash
curl -i -X GET \
  -b cookies.txt \
  http://localhost:8080/api/auth/me
```

#### Access a protected endpoint

```bash
curl -i -X GET \
  -b cookies.txt \
  http://localhost:8080/api/secure/ping
```

#### Logout

```bash
curl -i -X POST \
  -b cookies.txt \
  http://localhost:8080/api/auth/logout
```
