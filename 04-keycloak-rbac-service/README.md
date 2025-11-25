
# Keycloak & Spring Boot Stateless RBAC

**Author:** Alae Touba  
**Context:** Personal IAM Lab

A stateless Resource Server implementation using **Spring Boot 3.4** and **Keycloak 26**. The primary goal of this project is to enable **token-based user authentication** and secure API access without relying on session cookies.

It serves as a laboratory to explore specific IAM patterns—specifically Audience Validation, Role-Based Access Control (RBAC), and Attribute Mapping—without the complexity of a frontend application. For educational clarity, this project uses the **OAuth2 Resource Owner Password Credentials Grant** (Direct Access Grants) to demonstrate how to obtain and validate tokens via REST APIs.

---

## 🚀 Tech Stack

*   **Java:** 17+
*   **Framework:** Spring Boot 3.4.0
*   **Identity Provider:** Keycloak 26.4.5 (running in Docker)
*   **Database:** PostgreSQL 16 (for Keycloak persistence)
*   **Build Tool:** Maven

## 🏗️ Architecture

This service is designed as a **Stateless Resource Server**. It does not maintain user sessions but instead relies on validating JWTs (JSON Web Tokens) issued by Keycloak.

**Key Concepts Explored:**
*   **Statelessness:** The API uses `SessionCreationPolicy.STATELESS`, validating the token on every request.
*   **Audience Validation:** Implementation of a custom validator to ensure tokens are explicitly intended for this service (`spring-api-client`).
*   **RBAC (Role-Based Access Control):** Mapping Keycloak Realm Roles (`API-READER`, `API-WRITER`) to Spring Security Authorities.
*   **Attribute Propagation:** Mapping custom user attributes (e.g., `department`) into JWT claims to be accessible via the `AuthenticationPrincipal`.

---

## 🛠️ Prerequisites

*   **Docker & Docker Compose:** For running Keycloak and PostgreSQL.
*   **Java 17+ SDK:** For running the Spring Boot application.
*   **Maven:** (Optional, wrapper included)

---

## 🏁 Getting Started

### 1. Infrastructure Setup (Docker)

Start the Identity Infrastructure (Keycloak + Postgres):

```bash
docker-compose up -d
```

*   **Keycloak Console:** [http://localhost:8080](http://localhost:8080)
*   **Admin Credentials:** `admin` / `admin`

### 2. Keycloak Configuration (Manual Lab)

Since this is a manual lab, you need to configure Keycloak to match the application's security requirements.

1.  **Create Realm:** `iam-lab-realm`
2.  **Define Attribute Schema (Required for Keycloak 26):**
    *   Go to **Realm Settings** -> **User Profile** -> **Create Attribute**.
    *   Name: `department`, Display Name: `Department`.
    *   Save.
3.  **Create Client:**
    *   **Client ID:** `spring-api-client`
    *   **Capabilities:** Client authentication (Confidential), Service accounts enabled, Standard flow, **Direct access grants** (Required for Password Grant).
4.  **Configure Mappers (in Client Scopes -> spring-api-client-dedicated):**
    *   **Audience Mapper:** Name: `audience-mapper`, Included Custom Audience: `spring-api-client` (Type manually).
    *   **Department Mapper:** Name: `department-mapper`, User Attribute: `department`, Token Claim Name: `department`.
5.  **Create Roles:** `API-READER`, `API-WRITER`.
6.  **Create Users:**
    *   **Reader:** `reader` / `password` (Role: `API-READER`, Department: `IT-Security`)
    *   **Writer:** `writer` / `password` (Role: `API-WRITER`, Department: `IT-Dev`)

*(See `initial-plan.md` for detailed step-by-step configuration)*

### 3. Run the Application

Start the Spring Boot service:

```bash
./mvnw spring-boot:run
```

The service runs on **port 8081**.

---

## 🔌 API Endpoints

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/public` | Public | Open endpoint, no auth required. |
| `GET` | `/api/reader` | `API-READER` | Restricted to users with the Reader role. |
| `GET` | `/api/writer` | `API-WRITER` | Restricted to users with the Writer role. |
| `GET` | `/api/profile` | `API-READER` | Returns user profile & attributes. |

---

## 🧪 Verification

You can verify the security implementation using `curl`.

**1. Get a Token (Password Grant Flow):**
*Note: This flow exchanges credentials directly for a token. It is used here specifically for API testing.*
```bash
curl -X POST http://localhost:8080/realms/iam-lab-realm/protocol/openid-connect/token \
  -d "client_id=spring-api-client" \
  -d "client_secret=<YOUR_CLIENT_SECRET>" \
  -d "username=reader" \
  -d "password=password" \
  -d "grant_type=password"
```

**2. Access Protected Endpoint:**
```bash
curl -H "Authorization: Bearer <ACCESS_TOKEN>" http://localhost:8081/api/profile
```

**3. Test RBAC (Writer Endpoint with Reader Token):**
```bash
# Should fail with 403 Forbidden
curl -H "Authorization: Bearer <ACCESS_TOKEN>" http://localhost:8081/api/writer
```

---

## 📜 License

This project is for educational purposes as part of a Personal IAM Lab.