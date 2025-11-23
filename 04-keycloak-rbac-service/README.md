# Production-Grade Stateless RBAC Service

**Author:** Alae Touba  
**Context:** Personal IAM Lab

A production-grade, stateless Resource Server implementation using **Spring Boot 3.5** and **Keycloak 26**. This project demonstrates advanced security patterns including Audience Validation, Role-Based Access Control (RBAC), and Attribute-Based Access Control (ABAC) without relying on complex test containers, favoring manual verification for clarity and control.

---

## 🚀 Tech Stack

*   **Java:** 17+
*   **Framework:** Spring Boot 3.5.8
*   **Identity Provider:** Keycloak 26.4.5 (running in Docker)
*   **Database:** PostgreSQL 16 (for Keycloak persistence)
*   **Build Tool:** Maven

## 🏗️ Architecture

This service is designed as a **Stateless Resource Server**. It does not maintain user sessions but instead relies on validating JWTs (JSON Web Tokens) issued by Keycloak.

Key Security Features:
*   **Statelessness:** No server-side sessions; scalable and RESTful.
*   **Audience Validation:** Ensures tokens are explicitly intended for this service (`spring-api-client`).
*   **RBAC (Role-Based Access Control):** Fine-grained access control using Keycloak Realm Roles (`API-reader`, `API-writer`).
*   **ABAC (Attribute-Based Access Control):** Access to user attributes (e.g., `department`) extracted from the token.

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

### 2. Keycloak Configuration

Since this is a manual lab, you need to configure Keycloak. Access the console and perform the following:

1.  **Create Realm:** `iam-lab-realm`
2.  **User Profile Schema (v26 Requirement):**
    *   Go to **Realm settings** -> **User profile** tab.
    *   Click **Create attribute**.
    *   **Name:** `department`, **Display Name:** `Department`.
    *   Save.
3.  **Create Client:**
    *   **Client ID:** `spring-api-client`
    *   **Capabilities:** Client authentication (Confidential), Service accounts enabled, Standard flow, Direct access grants.
4.  **Configure Mappers (in Client Scopes -> spring-api-client-dedicated):**
    *   **Audience Mapper:** Name: `audience-mapper`, Included Custom Audience: `spring-api-client`.
    *   **Department Mapper:** Name: `department-mapper`, User Attribute: `department`, Token Claim Name: `department`.
5.  **Create Roles:** `API-reader`, `API-writer`.
6.  **Create Users:**
    *   **Reader:** `reader` / `password` (Role: `API-reader`, Department: `IT-Security`)
    *   **Writer:** `writer` / `password` (Role: `API-writer`, Department: `IT-Dev`)

*(See `initial-plan.md` for detailed step-by-step configuration)*

### 3. Run the Application

Start the Spring Boot service:

```bash
./run.sh
```
*Or manually:* `./mvnw spring-boot:run`

The service runs on **port 8081**.

---

## 🔌 API Endpoints

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/public` | Public | Open endpoint, no auth required. |
| `GET` | `/api/reader` | `API-reader` | Restricted to users with the Reader role. |
| `GET` | `/api/writer` | `API-writer` | Restricted to users with the Writer role. |
| `GET` | `/api/profile` | `API-reader` | Returns user profile & attributes (ABAC demo). |

---

## 🧪 Verification

You can verify the security implementation using `curl`.

**1. Get a Token (Reader):**
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
