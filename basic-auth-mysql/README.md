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