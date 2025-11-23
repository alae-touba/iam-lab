
***

# Project Specification: Production-Grade Stateless RBAC Service

**Author:** Alae Touba\
**Context:** Personal IAM Lab\
**Stack:** Java 17+, Spring Boot 3.5.8, Keycloak 26.4.5 (Docker)\
**Architecture:** Stateless Resource Server with Audience Validation, RBAC (Roles), and ABAC (Attributes).

---

## Part 0: Infrastructure (Docker)

Save this as `docker-compose.yml` in your infrastructure folder. This uses the latest Keycloak v26 standards.

```yaml
services:
  # 1. Database (Persists your Realm/Users between restarts)
  postgres:
    image: postgres:16
    container_name: iam_lab_postgres
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - iam_network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 5s
      timeout: 5s
      retries: 5

  # 2. Keycloak Server (v26.4.5)
  keycloak:
    image: quay.io/keycloak/keycloak:26.4.5
    container_name: iam_lab_keycloak
    command: start-dev
    environment:
      # Database Connection
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: password
      
      # Admin Credentials (New v26+ format)
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      
      # Developer Settings
      KC_HTTP_ENABLED: "true"
      KC_HOSTNAME: localhost
      KC_LOG_LEVEL: INFO
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - iam_network

volumes:
  postgres_data:

networks:
  iam_network:
    driver: bridge
```

---

## Part 1: Keycloak Configuration (Manual Setup)

Access the console at [http://localhost:8080](http://localhost:8080) (`admin`/`admin`).

### 1. Realm & Token Settings
1.  **Create Realm:** Name it `iam-lab-realm`.
2.  **Increase Token Lifespan:**
    *   Go to **Realm settings** -> **Tokens** tab.
    *   Change **Access Token Lifespan** to `30 Minutes` (so tokens don't expire while testing).
    *   Save.

### 2. User Profile Schema (v26 Requirement)
1.  Go to **Realm settings** -> **User profile** tab.
2.  Click **Create attribute**.
    *   **Name:** `department`.
    *   **Display Name:** `Department`.
    *   Save.

### 3. Client Configuration
1.  Go to **Clients** -> **Create client**.
    *   **Client ID:** `spring-api-client`.
    *   **Capabilities:** Confidential (`On`), Service Accounts (`On`), Standard Flow (`On`), Direct Access Grants (`On`).
    *   **Save.**
2.  **Credentials:** Copy the `Client Secret` from the Credentials tab.

### 4. Mappers (Security & Data)
Go to **Clients** -> `spring-api-client` -> **Client scopes** -> `spring-api-client-dedicated`.

*   **Audience Mapper:**
    *   Add mapper -> Audience.
    *   Name: `audience-mapper`.
    *   Included Custom Audience: `spring-api-client` (Type this manually).
    *   Add to access token: `On`.
*   **Department Mapper:**
    *   Add mapper -> User Attribute.
    *   Name: `department-mapper`.
    *   User Attribute: `department`.
    *   Token Claim Name: `department`.
    *   Claim JSON Type: `String`.
    *   Add to access token: `On`.

### 5. Roles & Users
1.  **Create Roles:** `API-reader` and `API-writer`.
2.  **Create User `reader`:**
    *   **Fields:** Username: `reader`, Email: `reader@test.com`, First Name: `Reader`, Last Name: `User`.
    *   **Email Verified:** Toggle `On`.
    *   **Department:** `IT-Security` (in Details tab).
    *   **Credentials:** `password` (Temporary: `Off`).
    *   **Role Mapping:** Assign `API-reader`.
3.  **Create User `writer`:**
    *   **Fields:** Username: `writer`, Email: `writer@test.com`, First Name: `Writer`, Last Name: `User`.
    *   **Email Verified:** Toggle `On`.
    *   **Department:** `IT-Dev` (in Details tab).
    *   **Credentials:** `password` (Temporary: `Off`).
    *   **Role Mapping:** Assign `API-writer`.

---

## Part 2: Spring Boot Implementation

### 1. Dependencies (`pom.xml`)
Cleaned up to remove testing complexity.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
        <relativePath/>
    </parent>
    
    <groupId>com.alae.iam</groupId>
    <artifactId>keycloak-rbac-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Security & Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!-- Utilities -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

### 2. Configuration (`application.yaml`)

```yaml
server:
  port: 8081

spring:
  application:
    name: iam-rbac-service
  security:
    oauth2:
      resourceserver:
        jwt:
          # URL checks .well-known/openid-configuration
          issuer-uri: http://localhost:8080/realms/iam-lab-realm
          # Custom property: The token MUST have "aud": "spring-api-client"
          audiences: spring-api-client

logging:
  level:
    org.springframework.security: DEBUG
```

### 3. Security Config (`SecurityConfig.java`)
Handles Statelessness, Audience Validation, and Role Extraction.

```java
package com.alae.iam.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    private String requiredAudience;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder()) 
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(requiredAudience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String audience;
        AudienceValidator(String audience) { this.audience = audience; }
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            if (jwt.getAudience().contains(audience)) return OAuth2TokenValidatorResult.success();
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience missing", null));
        }
    }

    static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @SuppressWarnings("unchecked")
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || realmAccess.isEmpty()) return List.of();
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) return List.of();
            return roles.stream()
                .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
                .collect(Collectors.toList());
        }
    }
}
```

### 4. Controller (`TestController.java`)
```java
package com.alae.iam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
    @GetMapping("/api/writer")
    @PreAuthorize("hasRole('API-writer')")
    public String writerEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return "Access Granted: WRITER for " + jwt.getSubject();
    }

    @GetMapping("/api/profile")
    @PreAuthorize("hasRole('API-reader')")
    public Map<String, Object> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "username", jwt.getClaimAsString("preferred_username"),
            "department", jwt.getClaimAsString("department") != null ? jwt.getClaimAsString("department") : "Unknown",
            "audience", jwt.getAudience()
        );
    }
}
```

---

## Part 3: Verification (Curl)

Ensure Spring Boot is running: `./mvnw spring-boot:run`.

### 1. Authenticate as Reader
```bash
curl -X POST http://localhost:8080/realms/iam-lab-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=spring-api-client" \
  -d "client_secret=<YOUR_CLIENT_SECRET>" \
  -d "username=reader" \
  -d "password=password" \
  -d "grant_type=password"
```
*Copy the `access_token`.*

### 2. Verify Profile & Attributes
```bash
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8081/api/profile
```
**Expected:** `{"username":"reader", "department":"IT-Security", ...}`

### 3. Verify RBAC
```bash
# Should SUCCEED (200 OK)
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8081/api/reader

# Should FAIL (403 Forbidden)
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8081/api/writer
```

### 4. Authenticate as Writer (Check new user)
```bash
curl -X POST http://localhost:8080/realms/iam-lab-realm/protocol/openid-connect/token \
  -d "client_id=spring-api-client" \
  -d "client_secret=<YOUR_CLIENT_SECRET>" \
  -d "username=writer" \
  -d "password=password" \
  -d "grant_type=password"
```

### 5. Verify Writer Access
```bash
# Should SUCCEED (200 OK)
curl -H "Authorization: Bearer <WRITER_TOKEN>" http://localhost:8081/api/writer
```