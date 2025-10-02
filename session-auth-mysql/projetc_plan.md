# Session Auth (Spring Security) – Implementation Plan for CLI Agent

Goal: Implement classic **username/password login with server-side sessions** (JSESSIONID) exposed via REST endpoints (no HTML login page). Clients POST to `/api/auth/login`, receive a session cookie, and subsequent requests are permitted because they’re authenticated. Logout invalidates the session.



### Dependencies (start.spring.io)

* Spring Web
* Spring Security
* Spring Data JPA
* MySQL Driver
* Validation (Bean Validation)
* Lombok
* (Optional) DevTools

---

## 1) High-level behavior

1. Client POSTs credentials to `/api/auth/login` using `application/x-www-form-urlencoded` with fields `usernameOrEmail` and `password`.
2. On success, server returns **200 OK** with a small JSON body (profile or a minimal success payload) and sets **`JSESSIONID` cookie** (`HttpOnly`, `Secure` in prod, `SameSite=Lax` or `Strict` depending on UX). The SecurityContext stores the authenticated principal.
3. Any request to protected endpoints with the `JSESSIONID` cookie is considered authenticated.
4. `POST /api/auth/logout` invalidates the session and clears the cookie.
5. Optionally expose `GET /api/auth/me` to return the current principal (helps frontends know they’re logged in).

> **Why form-encoded?** `formLogin()` and `UsernamePasswordAuthenticationFilter` expect form fields. It’s the shortest path without a custom JSON auth filter. You can add a JSON variant later.

---

## 2) API Contract

### 2.1 `POST /api/auth/login`

* **Content-Type**: `application/x-www-form-urlencoded`
* **Body fields**:

  * `usernameOrEmail`: string
  * `password`: string
* **Success (200)**: JSON `{ "status": "ok", "user": { "id": ..., "username": ..., "authorities": [...] } }`

  * **Set-Cookie**: `JSESSIONID=...; Path=/; HttpOnly; Secure; SameSite=Lax` (Secure only in HTTPS)
* **Failure (401/403)**: JSON `{ "status": "error", "message": "Bad credentials" }`

### 2.2 `GET /api/auth/me`

* Returns the authenticated principal summary: `{ id, username, email, authorities }`

### 2.3 `POST /api/auth/logout`

* Invalidates the session; returns `204 No Content` or `{ "status": "ok" }` and a `Set-Cookie: JSESSIONID=; Max-Age=0; ...`.

### 2.4 Sample protected endpoint

* `GET /api/secure/ping` → `200 { "pong": true }` for authenticated users only.

> **CSRF**
>
> * DEV/CLI workflows: **disable CSRF** to simplify Postman/cURL testing.
> * PROD/Browsers: **enable CSRF** and provide `GET /api/csrf` to read the token from cookie+header; clients must echo it in `X-CSRF-TOKEN`.

---

## 3) Security configuration

### 3.1 Session settings

* Session fixation protection: `migrateSession` (default)
* Concurrent sessions (optional): `maximumSessions(1)` with `preventsLogin(false)`

### 3.2 Password encoding

* Use BCrypt with strength 10–12.

### 3.3 Authentication

* `DaoAuthenticationProvider` + `UserDetailsService` backed by MySQL (`users`, `authorities` tables).

### 3.4 Filter chain (key points)

* `.authorizeHttpRequests` → permit `/health`, `/actuator/**` (if added), `/api/auth/login`, `/api/auth/me` (decide if public or requires auth), `/error`.
* All other endpoints require authentication.
* `.formLogin()` with:

  * `.loginProcessingUrl("/api/auth/login")`
  * `.usernameParameter("usernameOrEmail")`
  * `.passwordParameter("password")`
  * **Custom** success/failure handlers that return JSON (not HTML redirects).
* `.logout()` with `.logoutUrl("/api/auth/logout")` and cookie clearing.
* `.csrf(csrf -> csrf.disable())` in DEV profile; Production profile provides CSRF token endpoint.

### 3.5 Cookie

* `HttpOnly` always.
* `Secure` in HTTPS.
* `SameSite=Lax` (works for most app flows) or `Strict` if you don’t need cross-site navigations. Avoid `None` unless using cross-site scenarios with HTTPS.

---

## 4) Data model & persistence

### 4.1 Entities

* **User**: `id (UUID/long)`, `username`, `email`, `password`, `enabled`, `accountNonLocked`, `accountNonExpired`, `credentialsNonExpired`, timestamps.
* **Authority**: `id`, `name` (e.g., `ROLE_USER`, `ROLE_ADMIN`).
* **UserAuthority** (if many-to-many) or simple one-to-many if prefer.

### 4.2 Repositories

* `UserRepository`: find by `username` or `email` (for `usernameOrEmail`).
* `AuthorityRepository` (optional if pre-seeded).


---

## 5) JSON success/failure handlers

Implement `AuthenticationSuccessHandler` and `AuthenticationFailureHandler` to write JSON to the response body. On success:

* Build a `UserSummary` DTO from `Authentication#getPrincipal()`.
* Return 200 and the DTO.
  On failure:
* Map `BadCredentialsException`, `DisabledException`, `LockedException` to 401/423 with `{ message }`.

---

## 6) Error handling

* Add a `@ControllerAdvice` to return consistent JSON for errors.
* Ensure `/error` produces JSON.





## 10) Testing plan

### 10.1 Unit

* `PasswordEncoder` bean exists and encodes+matches.
* `UserDetailsService` resolves by username **or** email.

### 10.2 Integration (MockMvc)

* `/api/auth/login` with valid creds → 200 + cookie + JSON user.
* `/api/secure/ping` unauthenticated → 401.
* After login (MockMvc session), `/api/secure/ping` → 200.
* `/api/auth/logout` invalidates session → subsequent `/api/secure/ping` → 401.


## 11) Security best practices

* Always store passwords using **BCrypt**.
* Return generic login errors (don’t leak which field is wrong).
* Consider rate limiting for login endpoint.
* Consider account lockout after N failures.
* In prod, enable CSRF and HTTPS; set `Secure` cookie.
* Log auth events (success/failure) with care (no passwords).

---

## 12) Work plan for the CLI agent (step-by-step tasks)

> **Root package**: `com.alae.iam.sessionauth`

### Task 1 — Project scaffold

* Generate project with dependencies listed in Section 0.
* Set Java 17

### Task 2 — Configuration & profiles

* Create `application.yml` (dev profile):

  * MySQL connection, JPA `ddl-auto: validate` , logging.
  * `server.servlet.session.cookie.same-site: Lax`
* Create `application-prod.yml` with CSRF enabled and cookie `Secure: true`.


### Task 4 — Domain & repositories

* Create `User`, `Authority` entities; map relations.
* Create `UserRepository` with `findByUsernameOrEmail(String identifier)`.

### Task 5 — Security beans

* `PasswordEncoder` (BCrypt)
* `UserDetailsService` that resolves by username/email.
* `DaoAuthenticationProvider` wired with encoder+UDS.

### Task 6 — JSON auth handlers

* `RestAuthenticationSuccessHandler` → returns `{ status: "ok", user: {...} }`.
* `RestAuthenticationFailureHandler` → returns `{ status: "error", message: ... }` with appropriate HTTP codes.

### Task 7 — SecurityFilterChain

* Permit `/api/auth/login`, `/error`, `/health` (if added).
* Require auth for `/api/**` (except permitted ones).
* `.formLogin().loginProcessingUrl("/api/auth/login").usernameParameter("usernameOrEmail").passwordParameter("password").successHandler(...).failureHandler(...)`.
* `.logout().logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")`.
* `csrf.disable()` in `dev` profile only (via conditional config or property flag).
* Configure CORS to allow credentials when needed.

### Task 8 — Controllers

* `AuthController`:

  * `GET /api/auth/me` returns `UserSummary` built from `SecurityContextHolder`.
* `SecureController`:

  * `GET /api/secure/ping` returns `{ "pong": true }`.

### Task 9 — Global error handling

* `@ControllerAdvice` mapping to JSON errors.

### Task 10 — Tests

* MockMvc tests as per Section 10.2.
* Optional: Testcontainers MySQL integration test.

### Task 11 — Dev tooling

* cURL scripts under `scripts/` for login, me, secure, logout (Section 9).
* Postman collection export under `postman/` with environment vars.

### Task 12 — README

* Explain flow, endpoints, cookie behavior, CSRF strategy (dev vs prod), how to run locally, and how to test via cURL/Postman.

---

## 13) Definition of Done (DoD)

* ✅ Login returns 200 with JSON and sets `JSESSIONID`.
* ✅ Authenticated requests to `/api/secure/**` return 200; unauthenticated → 401.
* ✅ Logout invalidates session; subsequent `/api/secure/**` → 401.
* ✅ Tests cover login success/failure and session usage.
* ✅ README documents usage and security notes.

---

## 14) Future enhancements (backlog)

* Add JSON-based login endpoint using a custom filter.
* Add Remember-Me cookie (hash-based persistent login).
* MFA (TOTP) and session re-auth for sensitive actions.
* Account lockout, captcha / proof-of-work on repeated failures.
* Audit logs and admin endpoints.
* Distributed session management (Spring Session + Redis).
