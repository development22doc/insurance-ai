# API Reference Documentation

**Version:** 1.0.0  
**Last Updated:** 2026-08-04  
**Base URL:** `http://localhost:8080` (local) | Production: `https://api.claimassist.io`

---

## 📋 Table of Contents

- [Authentication APIs](#authentication-apis)
- [Customer APIs](#customer-apis)
- [Claims APIs](#claims-apis)
- [Agent APIs](#agent-apis)
- [Health & Admin APIs](#health--admin-apis)
- [Error Handling](#error-handling)
- [Status Codes](#status-codes)

---

## Authentication APIs

### OAuth2 Token Endpoint

**Obtain Access Token** (Password Flow)

```http
POST /auth/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&username=user@example.com&password=password&client_id=claimassist-customer-app
```

**Response (200 OK)**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cC...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cC...",
  "scope": "openid profile email"
}
```

### OAuth2 Token Refresh

```http
POST /auth/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token=<refresh_token>&client_id=claimassist-customer-app
```

### PKCE Authorization Code Flow

```http
GET /auth/authorize?client_id=claimassist-customer-app&response_type=code&redirect_uri=https://app.example.com/callback&scope=openid%20profile&code_challenge=<challenge>&code_challenge_method=S256
```

**Callback with Code**
```
https://app.example.com/callback?code=authorization_code&session_state=...
```

**Exchange Code for Token**
```http
POST /auth/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=authorization_code&redirect_uri=https://app.example.com/callback&client_id=claimassist-customer-app&code_verifier=<verifier>
```

---

## Customer APIs

### Register Customer

```http
POST /api/customers/register
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+1234567890",
  "password": "SecurePassword123!"
}
```

**Response (201 Created)**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "createdAt": "2026-08-04T10:00:00Z"
}
```

### Login Customer

```http
POST /api/customers/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePassword123!"
}
```

**Response (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cC...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### Get Customer Profile

```http
GET /api/customers/profile
Authorization: Bearer {access_token}
```

**Response (200 OK)**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+1234567890",
  "createdAt": "2026-08-04T10:00:00Z",
  "updatedAt": "2026-08-04T10:00:00Z"
}
```

### Update Customer Profile

```http
PUT /api/customers/profile
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+1234567890"
}
```

**Response (200 OK)**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+1234567890",
  "updatedAt": "2026-08-04T10:15:00Z"
}
```

### Get Customer Policies

```http
GET /api/customers/policies
Authorization: Bearer {access_token}
```

**Response (200 OK)**
```json
{
  "policies": [
    {
      "id": "POL-2024-001",
      "type": "AUTO_INSURANCE",
      "status": "ACTIVE",
      "startDate": "2024-01-01",
      "endDate": "2025-01-01",
      "premium": 1200.00,
      "coverage": {
        "liability": 100000,
        "collision": 50000,
        "comprehensive": 50000
      }
    }
  ],
  "totalCount": 1
}
```

---

## Claims APIs

### Submit Claim

```http
POST /api/claims
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "policyId": "POL-2024-001",
  "claimType": "AUTO_ACCIDENT",
  "description": "Minor rear-end collision",
  "incidentDate": "2026-08-04",
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "address": "123 Main St, City, State 12345"
  },
  "claimants": [
    {
      "type": "INSURED",
      "name": "John Doe",
      "email": "john@example.com",
      "phone": "+1234567890"
    }
  ]
}
```

**Response (201 Created)**
```json
{
  "id": "CLM-2026-000001",
  "policyId": "POL-2024-001",
  "claimType": "AUTO_ACCIDENT",
  "status": "SUBMITTED",
  "description": "Minor rear-end collision",
  "createdAt": "2026-08-04T10:00:00Z",
  "updatedAt": "2026-08-04T10:00:00Z",
  "_links": {
    "self": {"href": "/api/claims/CLM-2026-000001"},
    "documents": {"href": "/api/claims/CLM-2026-000001/documents"}
  }
}
```

### Get Claim Details

```http
GET /api/claims/{claimId}
Authorization: Bearer {access_token}
```

**Response (200 OK)**
```json
{
  "id": "CLM-2026-000001",
  "policyId": "POL-2024-001",
  "claimType": "AUTO_ACCIDENT",
  "status": "UNDER_REVIEW",
  "description": "Minor rear-end collision",
  "estimatedAmount": 5000.00,
  "approvedAmount": null,
  "documents": [
    {
      "id": "DOC-001",
      "type": "POLICE_REPORT",
      "filename": "police_report.pdf",
      "uploadedAt": "2026-08-04T10:15:00Z",
      "status": "APPROVED"
    }
  ],
  "createdAt": "2026-08-04T10:00:00Z",
  "updatedAt": "2026-08-04T10:30:00Z"
}
```

### Get My Claims

```http
GET /api/claims?status=SUBMITTED&limit=10&offset=0
Authorization: Bearer {access_token}
```

**Query Parameters:**
- `status` (optional): SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, PAID
- `limit` (optional, default: 20): Results per page
- `offset` (optional, default: 0): Pagination offset

**Response (200 OK)**
```json
{
  "claims": [
    {
      "id": "CLM-2026-000001",
      "policyId": "POL-2024-001",
      "status": "UNDER_REVIEW",
      "createdAt": "2026-08-04T10:00:00Z"
    },
    {
      "id": "CLM-2026-000002",
      "policyId": "POL-2024-001",
      "status": "APPROVED",
      "createdAt": "2026-08-04T09:00:00Z"
    }
  ],
  "totalCount": 2,
  "page": 1,
  "pageSize": 10
}
```

### Update Claim Status

```http
PUT /api/claims/{claimId}/status
Authorization: Bearer {internal_api_key}
Content-Type: application/json
X-Internal-API-Key: {internal_api_key}

{
  "status": "APPROVED",
  "reason": "Documentation verified",
  "approvedAmount": 5000.00
}
```

**Response (200 OK)**
```json
{
  "id": "CLM-2026-000001",
  "status": "APPROVED",
  "approvedAmount": 5000.00,
  "updatedAt": "2026-08-04T11:00:00Z"
}
```

### Upload Claim Document

```http
POST /api/claims/{claimId}/documents
Authorization: Bearer {access_token}
Content-Type: multipart/form-data

{
  "file": <binary_file>,
  "documentType": "POLICE_REPORT",
  "description": "Police report from incident"
}
```

**Response (201 Created)**
```json
{
  "id": "DOC-001",
  "claimId": "CLM-2026-000001",
  "filename": "police_report.pdf",
  "documentType": "POLICE_REPORT",
  "size": 1024000,
  "uploadedAt": "2026-08-04T10:15:00Z",
  "status": "UPLOADED"
}
```

### Get Claim Documents

```http
GET /api/claims/{claimId}/documents
Authorization: Bearer {access_token}
```

**Response (200 OK)**
```json
{
  "documents": [
    {
      "id": "DOC-001",
      "filename": "police_report.pdf",
      "documentType": "POLICE_REPORT",
      "size": 1024000,
      "uploadedAt": "2026-08-04T10:15:00Z",
      "status": "APPROVED"
    }
  ]
}
```

---

## Agent APIs

### Start Conversation

```http
POST /api/agent/conversations
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "claimId": "CLM-2026-000001",
  "context": "Help me understand the claim status"
}
```

**Response (201 Created)**
```json
{
  "conversationId": "CONV-001",
  "claimId": "CLM-2026-000001",
  "createdAt": "2026-08-04T10:00:00Z",
  "messages": []
}
```

### Send Message

```http
POST /api/agent/conversations/{conversationId}/messages
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "message": "What documents do I need to submit?"
}
```

**Response (200 OK)**
```json
{
  "messageId": "MSG-001",
  "conversationId": "CONV-001",
  "role": "USER",
  "content": "What documents do I need to submit?",
  "timestamp": "2026-08-04T10:01:00Z"
}
```

### Get Agent Response (Streaming)

```http
GET /api/agent/conversations/{conversationId}/messages?stream=true
Authorization: Bearer {access_token}
Accept: text/event-stream
```

**Response (200 OK - Server-Sent Events)**
```
event: message
data: {"role":"ASSISTANT","content":"You need to submit...","timestamp":"2026-08-04T10:01:05Z"}

event: message
data: {"role":"ASSISTANT","content":"...policy documents...","timestamp":"2026-08-04T10:01:06Z"}

event: done
data: {}
```

### Get Conversation History

```http
GET /api/agent/conversations/{conversationId}
Authorization: Bearer {access_token}
```

**Response (200 OK)**
```json
{
  "conversationId": "CONV-001",
  "claimId": "CLM-2026-000001",
  "messages": [
    {
      "messageId": "MSG-001",
      "role": "USER",
      "content": "What documents do I need to submit?",
      "timestamp": "2026-08-04T10:01:00Z"
    },
    {
      "messageId": "MSG-002",
      "role": "ASSISTANT",
      "content": "You need to submit policy documents...",
      "timestamp": "2026-08-04T10:01:05Z"
    }
  ]
}
```

---

## Health & Admin APIs

### Health Check

```http
GET /health
```

**Response (200 OK)**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "kafka": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

### Readiness Probe

```http
GET /health/ready
```

**Response (200 OK / 503 Service Unavailable)**
```json
{
  "status": "UP"
}
```

### Liveness Probe

```http
GET /health/live
```

**Response (200 OK / 503 Service Unavailable)**
```json
{
  "status": "UP"
}
```

### Metrics (Prometheus)

```http
GET /metrics
```

**Response (200 OK)**
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Survivor Space",} 2048576.0

# HELP http_requests_total Total HTTP requests
# TYPE http_requests_total counter
http_requests_total{handler="claims",method="POST",status="201"} 123.0
```

### Application Info

```http
GET /info
```

**Response (200 OK)**
```json
{
  "app": {
    "name": "Insurance AI Platform",
    "version": "1.0.0"
  },
  "build": {
    "timestamp": "2026-08-04T10:00:00Z"
  }
}
```

---

## Error Handling

### Error Response Format

```json
{
  "timestamp": "2026-08-04T10:00:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Validation failed",
  "path": "/api/claims",
  "errors": [
    {
      "field": "policyId",
      "message": "Policy ID is required",
      "code": "FIELD_REQUIRED"
    }
  ]
}
```

### Common Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| FIELD_REQUIRED | 400 | Required field is missing |
| FIELD_INVALID | 400 | Field value is invalid |
| INVALID_JWT | 401 | JWT token is invalid or expired |
| UNAUTHORIZED | 401 | Authentication required |
| FORBIDDEN | 403 | Insufficient permissions |
| NOT_FOUND | 404 | Resource not found |
| CONFLICT | 409 | Resource already exists |
| RATE_LIMIT_EXCEEDED | 429 | Rate limit exceeded |
| INTERNAL_ERROR | 500 | Internal server error |

---

## Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK - Request succeeded |
| 201 | Created - Resource created |
| 204 | No Content - Successful with no response body |
| 400 | Bad Request - Invalid input |
| 401 | Unauthorized - Authentication required |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 409 | Conflict - Resource conflict |
| 429 | Too Many Requests - Rate limit exceeded |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

---

## Authentication Headers

### JWT Bearer Token

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Internal API Key

```http
X-Internal-API-Key: internal-secret-key-12345
Authorization: Bearer internal-client-credentials-token
```

### Correlation ID (Automatic)

```http
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
X-Request-Id: req-12345-abcde
```

---

## Rate Limiting

**Global Rate Limits:**
- 100 requests per minute per IP
- 1000 requests per minute per authenticated user

**Response Headers:**
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1691146800
```

**When Exceeded (429 Too Many Requests):**
```json
{
  "status": 429,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Try again after 60 seconds",
  "retryAfter": 60
}
```

---

**Next:** Import [Postman Collection](./insurance-ai-platform.postman_collection.json) for interactive testing.
