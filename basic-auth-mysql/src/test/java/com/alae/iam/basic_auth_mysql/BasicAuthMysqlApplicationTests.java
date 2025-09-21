package com.alae.iam.basic_auth_mysql;

import com.alae.iam.basic_auth_mysql.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BasicAuthMysqlApplicationTests {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
            // .withReuse(true); // optional: enable with ~/.testcontainers.properties (testcontainers.reuse.enable=true)

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // If you want Hibernate to auto-create schema during tests:
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ---- helpers ------------------------------------------------------------

    private void register(String username, String email, String password) throws Exception {
        RegisterRequest req = new RegisterRequest(username, email, password);
        mockMvc.perform(post("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    // ---- smoke --------------------------------------------------------------

    @Test
    void contextLoads() { }

    // ---- public / auth flows -----------------------------------------------

    @Test
    void publicEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/public"))
                .andExpect(status().isOk())
                .andExpect(content().string("This is public"));
    }

    @Test
    void homeEndpointIsUnauthorizedWithoutAuth() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication failed: Full authentication is required to access this resource"));
    }

    @Test
    void shouldRegisterUser() throws Exception {
        register("testuser-register", "test-register@example.com", "password123");
    }

    @Test
    void homeEndpointIsAccessibleWithAuth() throws Exception {
        register("testuser-auth", "test-auth@example.com", "password123");

        mockMvc.perform(get("/home").with(httpBasic("testuser-auth", "password123")))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome, testuser-auth"))
                .andExpect(header().doesNotExist("Set-Cookie")); // stateless
    }

    @Test
    void homeWithWrongPassword_is401() throws Exception {
        register("badpass", "badpass@example.com", "password123");

        mockMvc.perform(get("/home").with(httpBasic("badpass", "WRONG")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void homeWithUnknownUser_is401() throws Exception {
        mockMvc.perform(get("/home").with(httpBasic("i-do-not-exist", "whatever")))
                .andExpect(status().isUnauthorized());
    }

    // ---- duplicates ---------------------------------------------------------

    @Test
    void shouldReturnConflictForDuplicateUsername() throws Exception {
        register("duplicateuser", "duplicate-user@example.com", "password123");

        RegisterRequest dup = new RegisterRequest("duplicateuser", "another-email@example.com", "password456");
        mockMvc.perform(post("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.error").value("Username or email already taken"));
    }

    @Test
    void shouldReturnConflictForDuplicateEmail() throws Exception {
        register("anotheruser", "duplicate-email@example.com", "password123");

        RegisterRequest dup = new RegisterRequest("anotheruser-2", "duplicate-email@example.com", "password456");
        mockMvc.perform(post("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.error").value("Username or email already taken"));
    }

    // ---- validation ---------------------------------------------------------

    @Test
    void shouldReturnBadRequestForShortUsername() throws Exception {
        RegisterRequest req = new RegisterRequest("ab", "short-user@example.com", "password123");
        mockMvc.perform(post("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    @Test
    void shouldReturnBadRequestForInvalidEmail() throws Exception {
        RegisterRequest req = new RegisterRequest("invalid-email-user", "invalid-email", "password123");
        mockMvc.perform(post("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    @Test
    void shouldReturnBadRequestForShortPassword() throws Exception {
        RegisterRequest req = new RegisterRequest("short-password-user", "short-pass@example.com", "1234");
        mockMvc.perform(post("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    // ---- statelessness ------------------------------------------------------

    @Test
    void shouldNotCreateSession() throws Exception {
        register("stateless-user", "stateless@example.com", "password123");

        mockMvc.perform(get("/home").with(httpBasic("stateless-user", "password123")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
