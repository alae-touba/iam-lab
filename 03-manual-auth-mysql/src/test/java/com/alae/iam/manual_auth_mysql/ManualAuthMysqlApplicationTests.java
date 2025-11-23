package com.alae.iam.manual_auth_mysql;

import com.alae.iam.manual_auth_mysql.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManualAuthMysqlApplicationTests {

  // ----- API Endpoints -----
  private static final String REGISTER = "/api/auth/register";
  private static final String LOGIN    = "/api/auth/login";
  private static final String LOGOUT   = "/api/auth/logout";
  private static final String ME       = "/api/auth/me";

  // ----- Test Data -----
  private static final String ALICE_USERNAME = "alice";
  private static final String ALICE_EMAIL = "alice@example.com";
  private static final String ALICE_PASSWORD = "secret";
  
  private static final String BOB_USERNAME = "bob";
  private static final String BOB_EMAIL = "bob@example.com";
  private static final String BOB_PASSWORD = "secret";
  
  private static final String WRONG_PASSWORD = "wrong";

  static {
    // Testcontainers + JNA tmp dir (prevents Windows/WSL temp issues)
    try {
      Path jnaTempDir = Path.of("target", "testcontainers-jna");
      Files.createDirectories(jnaTempDir);
      System.setProperty("jna.tmpdir", jnaTempDir.toAbsolutePath().toString());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to prepare JNA temp directory", e);
    }
  }

  private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.0");

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(MYSQL_IMAGE)
          .withDatabaseName("manual_auth_db")
          .withUsername("root")
          .withPassword("root");
          // .withReuse(true) // optional: speed up local runs

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository repo;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void cleanDatabase() {
    repo.deleteAll();
  }

  // ----- Tests -----

  @Nested
  @DisplayName("Auth flow")
  class AuthFlowTests {

    @Test
    @DisplayName("register → login → me → logout → me(401)")
    void loginMeLogoutHappyPath() throws Exception {
      // arrange
      registerUser(ALICE_USERNAME, ALICE_EMAIL, ALICE_PASSWORD);

      // act: login
      MockHttpSession session = loginAndGetSession(ALICE_USERNAME, ALICE_PASSWORD);
      assertNotNull(session, "No HttpSession created for login");

      // assert: me
      me(session)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value(ALICE_EMAIL));

      // act: logout
      logout(session).andExpect(status().isOk());

      // assert: me after logout
      me(session).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("registration creates user")
    void registrationCreatesUser() throws Exception {
      registerUser(BOB_USERNAME, BOB_EMAIL, BOB_PASSWORD);

      var user = repo.findByUsername(BOB_USERNAME).orElse(null);
      assertNotNull(user);
      assertEquals(BOB_EMAIL, user.getEmail());
    }

    @Test
    @DisplayName("login with wrong password returns 401 (ProblemDetail)")
    void loginWrongPasswordReturns401() throws Exception {
      registerUser(ALICE_USERNAME, ALICE_EMAIL, ALICE_PASSWORD);

      postJson(LOGIN, Map.of(
              "usernameOrEmail", ALICE_USERNAME,
              "password", WRONG_PASSWORD
          ))
          .andExpect(status().isUnauthorized())
          // If your handlers return RFC7807, these are nice to assert:
          .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/problem+json")))
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.title").exists())
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.errorCode").exists());
    }
  }

  // ----- Small test DSL helpers -----

  private void registerUser(String username, String email, String password) throws Exception {
    postJson(REGISTER, Map.of(
        "username", username,
        "email", email,
        "password", password
    ))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.username").value(username))
    .andExpect(jsonPath("$.email").value(email));
  }

  private MockHttpSession loginAndGetSession(String usernameOrEmail, String password) throws Exception {
    MvcResult res = postJson(LOGIN, Map.of(
        "usernameOrEmail", usernameOrEmail,
        "password", password
    ))
    .andExpect(status().isOk())
    .andReturn();

    return (MockHttpSession) res.getRequest().getSession(false);
  }

  private ResultActions me(MockHttpSession session) throws Exception {
    return mvc.perform(
        get(ME)
          .session(session)
          .accept(MediaType.APPLICATION_JSON)
    );
  }

  private ResultActions logout(MockHttpSession session) throws Exception {
    return mvc.perform(
        post(LOGOUT)
          .session(session)
          .accept(MediaType.APPLICATION_JSON)
    );
  }

  private ResultActions postJson(String url, Object body) throws Exception {
    return mvc.perform(
        post(url)
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(body))
    );
  }
}
