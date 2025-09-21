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
