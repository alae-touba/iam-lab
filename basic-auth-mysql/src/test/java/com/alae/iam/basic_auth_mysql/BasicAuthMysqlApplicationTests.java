package com.alae.iam.basic_auth_mysql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.alae.iam.basic_auth_mysql.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.isConflict;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class BasicAuthMysqlApplicationTests {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void publicEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/public"))
                .andExpect(status().isOk())
                .andExpect(content().string("This is public"));
    }

    @Test
    void homeEndpointIsUnauthorizedWithoutAuth() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRegisterUser() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("testuser-register", "test-register@example.com", "password123");

        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void homeEndpointIsAccessibleWithAuth() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("testuser-auth", "test-auth@example.com", "password123");

        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/home").with(httpBasic("testuser-auth", "password123")))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome, testuser-auth"));
    }

    @Test
    void shouldReturnBadRequestForDuplicateUsername() throws Exception {
        // Given: A user is already registered
        RegisterRequest initialRequest = new RegisterRequest("duplicateuser", "duplicate-user@example.com", "password123");
        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isOk());

        // When & Then: Attempt to register again with the same username
        RegisterRequest duplicateUsernameRequest = new RegisterRequest("duplicateuser", "another-email@example.com", "password456");
        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateUsernameRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username or email already taken"));
    }

    @Test
    void shouldReturnBadRequestForDuplicateEmail() throws Exception {
        // Given: A user is already registered
        RegisterRequest initialRequest = new RegisterRequest("anotheruser", "duplicate-email@example.com", "password123");
        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isOk());

        // When & Then: Attempt to register again with the same email
        RegisterRequest duplicateEmailRequest = new RegisterRequest("anotheruser-2", "duplicate-email@example.com", "password456");
        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateEmailRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username or email already taken"));
    }

    @Test
    void shouldReturnBadRequestForShortUsername() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("ab", "short-user@example.com", "password123");

        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForInvalidEmail() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("invalid-email-user", "invalid-email", "password123");

        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForShortPassword() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("short-password-user", "short-pass@example.com", "1234");

        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotCreateSession() throws Exception {
        // Given: A user is registered
        RegisterRequest registerRequest = new RegisterRequest("stateless-user", "stateless@example.com", "password123");
        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // When & Then: An authenticated request is made and no session is created
        mockMvc.perform(get("/home").with(httpBasic("stateless-user", "password123")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
