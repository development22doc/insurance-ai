# API Reference

**Version:** 1.0.0  
**Last Updated:** August 4, 2026  
**Base URL:** `http://localhost:8080` (local), `https://api.claimassist.com` (production)

---

## Authentication

All API requests require OAuth2 bearer token in Authorization header:

```
Authorization: Bearer {access_token}
```

**Token Formats:**
- **Access Token:** JWT (short-lived, ~1 hour)
- **Refresh Token:** Opaque (long-lived, ~7 days)

---

## Claim Endpoints

### Create Claim

**Request:**
```
POST /api/claims
Content-Type: application/json
Authorization: Bearer {access_token}

{
  "policyId": "660e8400-e29b-41d4-a716-446655440001",
  "claimType": "MEDICAL",
  "amount": 5000.00,
  "description": "Emergency room visit",
  "parties": [
    {
      "type": "CLAIMANT",
      "name": "John Doe",
      "email": "john@example.com"
    }
  ]
}
```

**Response:**
```
201 Created
Content-Type: application/json

{
  "claimId": "550e8400-e29b-41d4-a716-446655440000",
  "policyId": "660e8400-e29b-41d4-a716-446655440001",
  "status": "INITIATED",
  "amount": 5000.00,
  "createdAt": "2026-08-04T10:30:00Z"
}
```

**Error Responses:**
```
400 Bad Request
{
  "error": "INVALID_POLICY",
  "message": "Policy not found: 660e8400-e29b-41d4-a716-446655440001"
}

401 Unauthorized
{
  "error": "INVALID_TOKEN",
  "message": "Authorization token is invalid or expired"
}
```

---

### Get Claim

**Request:**
```
GET /api/claims/{claimId}
Authorization: Bearer {access_token}
```

**Response:**
```
200 OK

{
  "claimId": "550e8400-e29b-41d4-a716-446655440000",
  "policyId": "660e8400-e29b-41d4-a716-446655440001",
  "status": "UNDER_REVIEW",
  "amount": 5000.00,
  "evaluation": {
    "score": 0.85,
    "fraudRiskLevel": "LOW"
  },
  "createdAt": "2026-08-04T10:30:00Z",
  "updatedAt": "2026-08-04T10:31:00Z"
}
```

---

### List Claims

**Request:**
```
GET /api/claims?status=UNDER_REVIEW&pageSize=20&pageNumber=1
Authorization: Bearer {access_token}
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| status | string | No | Claim status filter |
| pageSize | integer | No | Records per page (default: 20, max: 100) |
| pageNumber | integer | No | Page number (default: 1) |
| sortBy | string | No | Sort field (createdAt, amount) |
| sortOrder | string | No | ASC or DESC |

**Response:**
```
200 OK

{
  "claims": [
    { /* claim object */ },
    { /* claim object */ }
  ],
  "totalCount": 150,
  "pageNumber": 1,
  "pageSize": 20,
  "totalPages": 8
}
```

---

### Update Claim Status

**Request:**
```
PATCH /api/claims/{claimId}/status
Content-Type: application/json
Authorization: Bearer {access_token}

{
  "newStatus": "APPROVED",
  "reason": "Approved by officer"
}
```

**Response:**
```
200 OK

{
  "claimId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "APPROVED",
  "updatedAt": "2026-08-04T10:35:00Z"
}
```

---

### Upload Claim Document

**Request:**
```
POST /api/claims/{claimId}/documents
Content-Type: multipart/form-data
Authorization: Bearer {access_token}

Form Data:
- file: {binary file content}
- documentType: INVOICE
```

**Response:**
```
201 Created

{
  "documentId": "880e8400-e29b-41d4-a716-446655440003",
  "claimId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "invoice.pdf",
  "documentType": "INVOICE",
  "uploadedAt": "2026-08-04T10:36:00Z"
}
```

---

## Customer Endpoints

### Get Customer

**Request:**
```
GET /api/customers/{customerId}
Authorization: Bearer {access_token}
```

**Response:**
```
200 OK

{
  "customerId": "770e8400-e29b-41d4-a716-446655440002",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+1-555-0123",
  "kycStatus": "VERIFIED"
}
```

---

### Get Customer Policies

**Request:**
```
GET /api/customers/{customerId}/policies
Authorization: Bearer {access_token}
```

**Response:**
```
200 OK

{
  "policies": [
    {
      "policyId": "660e8400-e29b-41d4-a716-446655440001",
      "policyType": "MEDICAL",
      "coverageAmount": 100000.00,
      "premium": 1200.00,
      "status": "ACTIVE"
    }
  ]
}
```

---

## Health & Status Endpoints

### Application Health

**Request:**
```
GET /actuator/health
```

**Response:**
```
200 OK

{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

---

### Metrics

**Request:**
```
GET /actuator/metrics
Authorization: Optional (Bearer token)
```

**Prometheus Format:**
```
GET /actuator/prometheus
```

---

## Error Handling

### Standard Error Format

```json
{
  "timestamp": "2026-08-04T10:30:45Z",
  "error": "CLAIM_NOT_FOUND",
  "message": "Claim with ID 550e8400-e29b-41d4-a716-446655440000 not found",
  "path": "/api/claims/550e8400-e29b-41d4-a716-446655440000",
  "status": 404
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK - Request succeeded |
| 201 | Created - Resource created |
| 400 | Bad Request - Invalid input |
| 401 | Unauthorized - Authentication required |
| 403 | Forbidden - Insufficient permissions |
| 404 | Not Found - Resource not found |
| 409 | Conflict - State conflict |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

---

## Rate Limiting

Requests are rate-limited to prevent abuse:

**Limits:**
- Per minute: 100 requests
- Per hour: 5000 requests

**Headers in Response:**
```
RateLimit-Limit: 100
RateLimit-Remaining: 95
RateLimit-Reset: 1628091015
```

**Exceeding Limit:**
```
429 Too Many Requests

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit of 100 requests per minute exceeded",
  "retryAfter": 42
}
```

---

## Pagination

For list endpoints, use:

```
GET /api/claims?pageNumber=1&pageSize=20

Response:
{
  "data": [ /* items */ ],
  "pagination": {
    "pageNumber": 1,
    "pageSize": 20,
    "totalPages": 8,
    "totalCount": 150
  }
}
```

---

## Sorting

Sort results using `sortBy` and `sortOrder`:

```
GET /api/claims?sortBy=createdAt&sortOrder=DESC
```

**Valid sortBy values:**
- `createdAt` - Creation timestamp
- `updatedAt` - Last update timestamp
- `amount` - Claim amount
- `status` - Claim status

---

## Filtering

Combine multiple filters:

```
GET /api/claims?status=APPROVED&claimType=MEDICAL&minAmount=1000&maxAmount=10000
```

---

## Async Operations

Some operations trigger async background work:

```
POST /api/claims
Response: 202 Accepted

{
  "claimId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING"
}

// Check status
GET /api/claims/550e8400-e29b-41d4-a716-446655440000
Response:
{
  "status": "COMPLETED" or "PROCESSING" or "FAILED"
}
```

---

## API Versioning

API versioned via header:

```
Accept: application/vnd.claimassist.v1+json
```

**Supported Versions:**
- `v1` - Current (default)
- `v2` - Future (not yet available)

---

## OpenAPI/Swagger Documentation

Interactive API documentation available at:

**Local:** `http://localhost:8080/swagger-ui.html`  
**Production:** `https://api.claimassist.com/swagger-ui.html`

---


