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

## Testing

These tests use Testcontainers to spin up an ephemeral MySQL 8.4 instance, so make sure Docker Desktop (or another Docker engine) is running before you start.

Run the full test suite with the Maven wrapper:

```bash
./mvnw test
```

To run a specific test class—for example the MockMvc-based integration suite—use:

```bash
./mvnw test -Dtest=SessionAuthMysqlApplicationTests
```

If you have Testcontainers reuse enabled (via `~/.testcontainers.properties`), subsequent runs will be faster because the MySQL container is kept warm between executions.

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


## **How Authentication Works: A Deep Dive into Form Login and Server Sessions**

This project employs a robust and stateful authentication mechanism using Spring Security's classic `formLogin` combined with server-side sessions. In this model, the authentication state is managed within the `HttpSession` on the server, and a `JSESSIONID` cookie on the client acts as the key to this session.

The entire security flow is orchestrated by the `SecurityFilterChain` bean defined in `SecurityConfig.java`.

### **The Authentication Flow: A Step-by-Step Breakdown**

Here is a detailed walkthrough of the authentication process, from an initial unauthenticated request to subsequent secure interactions.

1.  **Attempting to Access a Protected Resource:**
    *   When a new client (without a session) tries to access a protected endpoint, the request is intercepted by Spring Security's filter chain.
    *   The framework checks the `SecurityContextHolder` for an `Authentication` object. Since none exists, access is denied.
    *   Control is then passed to the configured `AuthenticationEntryPoint`. In this application, our custom `RestAuthenticationEntryPoint` is invoked, which returns a `401 Unauthorized` status along with a JSON error message, signaling the client that authentication is required.

2.  **The User Logs In:**
    *   The client sends a `POST` request to the `/api/auth/login` endpoint. This request includes the user's credentials (`usernameOrEmail` and `password`) in the request body.

3.  **`UsernamePasswordAuthenticationFilter` Intercepts the Request:**
    *   The `.formLogin()` configuration in `SecurityConfig` activates this standard Spring Security filter. It is specifically designed to handle form-based login submissions.
    *   It listens for requests on the `loginProcessingUrl` (`/api/auth/login`) and extracts the username and password from the request.
    *   It then constructs an unauthenticated `UsernamePasswordAuthenticationToken` using these credentials.

4.  **Delegation to the `AuthenticationManager`:**
    *   The filter itself does not perform authentication. Instead, it delegates this responsibility to the `AuthenticationManager`.
    *   The `AuthenticationManager` (typically the `ProviderManager` implementation) iterates through the configured authentication providers to find one that supports the `UsernamePasswordAuthenticationToken`.

5.  **`DaoAuthenticationProvider` Authenticates the User:**
    *   The `DaoAuthenticationProvider` is the component that handles the actual credential validation.
    *   It calls the `loadUserByUsername()` method of our `CustomUserDetailsService` to retrieve the user's details from the MySQL database.
    *   If the user is found, the `BCryptPasswordEncoder` hashes the password provided in the login request and securely compares it to the stored hash in the database.

6.  **Handling a Successful Authentication:**
    *   Upon a successful password match, the `DaoAuthenticationProvider` returns a fully authenticated `UsernamePasswordAuthenticationToken`, which now includes the user's details and granted authorities (roles).
    *   This authenticated token is then stored in the `SecurityContext`. Spring Security's `SecurityContextPersistenceFilter` ensures this `SecurityContext` is saved in the `HttpSession`.
    *   The flow is then passed to our custom `RestAuthenticationSuccessHandler`. This handler serializes a `UserSummary` object into a JSON response, sending a `200 OK` status with the user's information.
    *   Finally, the server sends a `Set-Cookie` header in the response, containing the `JSESSIONID`. This cookie is by default configured as `HttpOnly` to mitigate XSS attacks and serves as the identifier for the server-side session.

7.  **Subsequent Authenticated Requests:**
    *   For every subsequent request to the application, the browser automatically includes the `JSESSIONID` cookie.
    *   The `SecurityContextPersistenceFilter` intercepts the request, reads the cookie, and uses its value to retrieve the `HttpSession`.
    *   It then repopulates the `SecurityContextHolder` with the `Authentication` object found in the session.
    *   Because the `SecurityContextHolder` now contains a valid `Authentication` object, the user is considered authenticated, and the request is allowed to proceed to the controller.

### **Session Lifecycle Management**

The way sessions are created and destroyed is critical for both security and performance.

*   **Session Creation Policy (`SessionCreationPolicy.IF_REQUIRED`):** This is the default and a very efficient strategy. A session is created only when it's needed—specifically, after a user successfully authenticates. This means that requests to public endpoints do not create a session, conserving server memory.
*   **Session Invalidation (Logout):** When the user calls `POST /api/auth/logout`:
    1.  The `HttpSession` is invalidated on the server, which clears the `SecurityContext`.
    2.  The client is instructed to delete the `JSESSIONID` cookie.
    3.  A `204 No Content` status is returned, signaling a successful logout.

### **Key Components and Their Roles**

*   **`SecurityFilterChain` (`SecurityConfig.java`):** This is the central piece of configuration that defines the entire security setup, including which endpoints are public, how login and logout are handled, and the session management policy.
*   **`.formLogin()`:** This convenient DSL configures the `UsernamePasswordAuthenticationFilter` and allows for customization of the login URL, request parameters, and success/failure handlers.
*   **`CustomUserDetailsService`:** This service acts as the bridge between Spring Security and your application's user model, responsible for loading user data from the database.
*   **`PasswordEncoder` (`BCrypt`):** A critical component for secure password handling. It uses a strong, one-way hashing algorithm to protect user credentials.
*   **Custom Handlers and Entry Point:**
    *   **`RestAuthenticationSuccessHandler`:** Ensures that a successful login returns a structured JSON response suitable for a REST API client.
    *   **`RestAuthenticationFailureHandler`:** Provides a consistent JSON error response for failed login attempts.
    *   **`RestAuthenticationEntryPoint`:** Guards protected endpoints by returning a `401 Unauthorized` JSON response when an unauthenticated request is detected.
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
