package com.alae.iam.session_auth_mysql;

import com.alae.iam.session_auth_mysql.domain.Authority;
import com.alae.iam.session_auth_mysql.dto.RegisterRequest;
import com.alae.iam.session_auth_mysql.repository.AuthorityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SessionAuthMysqlApplicationTests {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("session_auth_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthorityRepository authorityRepository;

    @BeforeEach
    void ensureRoleUserExists() {
        authorityRepository.findByName("ROLE_USER")
                .orElseGet(() -> authorityRepository.save(Authority.builder().name("ROLE_USER").build()));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void registeringANewUserReturnsCreatedSummary() throws Exception {
        TestUser user = randomUser();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(user.username(), user.email(), user.password()))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
    }

    @Test
    void loginCreatesAuthenticatedSession() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("usernameOrEmail", user.username())
                        .param("password", user.password()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT", notNullValue()))
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
    }

    @Test
    void meEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
    }

    @Test
    void meEndpointReturnsCurrentUserWhenLoggedIn() throws Exception {
        TestUser user = registerUser();
        LoginContext login = login(user);

        mockMvc.perform(get("/api/auth/me")
                        .session(login.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
    }

    @Test
    void securePingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/secure/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
    }

    @Test
    void securePingAccessibleWithSession() throws Exception {
        TestUser user = registerUser();
        LoginContext login = login(user);

        mockMvc.perform(get("/api/secure/ping")
                        .session(login.session()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pong").value(true));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        TestUser user = registerUser();
        LoginContext login = login(user);

        mockMvc.perform(post("/api/auth/logout")
                        .session(login.session()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                        .session(login.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
    }

    @Test
    void loginWithBadCredentialsReturnsUnauthorized() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("usernameOrEmail", user.username())
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Bad credentials"));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        String base = "duplicate-" + UUID.randomUUID();
        RegisterRequest request = new RegisterRequest(base, base + "@example.com", "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.message").value("User with this username or email already exists"));
    }

    private TestUser registerUser() throws Exception {
        TestUser user = randomUser();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(user.username(), user.email(), user.password()))))
                .andExpect(status().isCreated());
        return user;
    }

    private LoginContext login(TestUser user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("usernameOrEmail", user.username())
                        .param("password", user.password()))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        return new LoginContext(session);
    }

    private TestUser randomUser() {
        String username = "user-" + UUID.randomUUID();
        return new TestUser(username, username + "@example.com", "Password123!");
    }

    private record TestUser(String username, String email, String password) { }

    private record LoginContext(MockHttpSession session) { }
}
