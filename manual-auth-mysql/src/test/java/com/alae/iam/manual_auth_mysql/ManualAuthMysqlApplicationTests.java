package com.alae.iam.manual_auth_mysql;

import com.alae.iam.manual_auth_mysql.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManualAuthMysqlApplicationTests {

  static {
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

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Autowired AppUserRepository repo;
  @Autowired MockMvc mvc;

  @BeforeEach
  void setup() {
    repo.deleteAll();
  }

  @Test
  void login_me_logout_happy_path() throws Exception {
    // first register a user
    mvc.perform(post("/api/auth/register")
        .contentType("application/json")
        .content("""
                 {"username":"alice","email":"alice@example.com","password":"secret"}
                 """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.email").value("alice@example.com"));

    // 1) login
    MvcResult loginRes = mvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content("""
                 {"usernameOrEmail":"alice","password":"secret"}
                 """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andReturn();

    MockHttpSession session = (MockHttpSession) loginRes.getRequest().getSession(false);
    assertNotNull(session, "No HttpSession created for login response");

    // 2) me
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alice@example.com"));

    // 3) logout
    mvc.perform(post("/api/auth/logout").session(session))
        .andExpect(status().isOk());

    // 4) me after logout -> 401
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registration_creates_user() throws Exception {
    mvc.perform(post("/api/auth/register")
        .contentType("application/json")
        .content("""
                 {"username":"bob","email":"bob@example.com","password":"secret"}
                 """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("bob"))
        .andExpect(jsonPath("$.email").value("bob@example.com"));

    // verify user was created
    var user = repo.findByUsername("bob").orElse(null);
    assertNotNull(user);
    assertEquals("bob@example.com", user.getEmail());
  }

  @Test
  void login_wrong_password_returns_401() throws Exception {
    // register first
    mvc.perform(post("/api/auth/register")
        .contentType("application/json")
        .content("""
                 {"username":"alice","email":"alice@example.com","password":"secret"}
                 """))
        .andExpect(status().isCreated());

    mvc.perform(post("/api/auth/login")
        .contentType("application/json")
        .content("""
                 {"usernameOrEmail":"alice","password":"wrong"}
                 """))
        .andExpect(status().isUnauthorized());
  }
}
