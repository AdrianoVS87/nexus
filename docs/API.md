# Nexus API Reference

Base URL: `http://localhost:8080` (via API Gateway)

All requests are routed through the API Gateway, which handles rate limiting, CORS, and JWT validation.

---

## Authentication

### Generate Token

```
POST /api/v1/auth/token
```

**Request Body (optional):**
```json
{
  "subject": "john.doe"
}
```

**curl:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject": "john.doe"}' | jq .
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9...",
  "expiresIn": "3600",
  "tokenType": "Bearer"
}
```

Use the returned token in subsequent requests:
```
Authorization: Bearer <token>
```

---

## Orders

### Create Order

```
POST /api/v1/orders
```

**Request Body:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "productName": "Mechanical Keyboard",
      "quantity": 1,
      "unitPrice": 149.99
    }
  ]
}
```

**curl:**
```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "productName": "Mechanical Keyboard",
        "quantity": 1,
        "unitPrice": 149.99
      }
    ]
  }' | jq .
```

**Response (201 Created):**
```json
{
  "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "totalAmount": 149.99,
  "currency": "USD",
  "items": [
    {
      "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "productName": "Mechanical Keyboard",
      "quantity": 1,
      "unitPrice": 149.99
    }
  ],
  "createdAt": "2026-04-03T21:00:00Z",
  "updatedAt": "2026-04-03T21:00:00Z"
}
```

### Get Order

```
GET /api/v1/orders/{id}
```

**curl:**
```bash
curl -s http://localhost:8080/api/v1/orders/d290f1ee-6c54-4b01-90e6-d701748f0851 | jq .
```

**Response (200 OK):**
```json
{
  "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CONFIRMED",
  "totalAmount": 149.99,
  "currency": "USD",
  "items": [...],
  "createdAt": "2026-04-03T21:00:00Z",
  "updatedAt": "2026-04-03T21:00:05Z"
}
```

### Get User Orders

```
GET /api/v1/orders?userId={userId}&page=0&size=20
```

**curl:**
```bash
curl -s "http://localhost:8080/api/v1/orders?userId=550e8400-e29b-41d4-a716-446655440000&page=0&size=20" | jq .
```

---

## Products

### List Products

```
GET /api/v1/products
```

**curl:**
```bash
curl -s http://localhost:8080/api/v1/products | jq .
```

**Response (200 OK):**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Mechanical Keyboard",
    "description": "Cherry MX Brown switches, RGB backlit, aluminum frame",
    "price": 149.99,
    "currency": "USD",
    "stockQuantity": 50
  }
]
```

### Get Product

```
GET /api/v1/products/{id}
```

**curl:**
```bash
curl -s http://localhost:8080/api/v1/products/a1b2c3d4-e5f6-7890-abcd-ef1234567890 | jq .
```

---

## WebSocket — Real-Time Order Updates

The Notification Service pushes order status changes over WebSocket. The API Gateway proxies WebSocket connections on the `/ws/**` path.

### Connection

```
ws://localhost:8080/ws/orders
```

**JavaScript:**
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/orders');

ws.onopen = () => {
  console.log('Connected to order updates');
};

ws.onmessage = (event) => {
  const update = JSON.parse(event.data);
  console.log(`Order ${update.orderId}: ${update.status}`);
};

ws.onclose = () => {
  console.log('Disconnected');
};
```

**CLI (using websocat):**
```bash
websocat ws://localhost:8080/ws/orders
```

### Message Format

Messages are pushed by the server when an order transitions state:

```json
{
  "orderId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "status": "CONFIRMED",
  "timestamp": "2026-04-03T21:00:05Z"
}
```

**Possible statuses:** `PENDING`, `PAYMENT_COMPLETED`, `INVENTORY_RESERVED`, `CONFIRMED`, `CANCELLED`

---

## Health Checks

All services expose Spring Boot Actuator health endpoints.

### Gateway Health (aggregated)

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Returns the gateway's own health along with downstream service statuses.

### Individual Services

| Service | Endpoint |
|---------|----------|
| Order Service | `http://localhost:8081/actuator/health` |
| Payment Service | `http://localhost:8082/actuator/health` |
| Inventory Service | `http://localhost:8083/actuator/health` |
| Notification Service | `http://localhost:8084/actuator/health` |

### Prometheus Metrics

```bash
curl -s http://localhost:8080/actuator/prometheus
```

---

## Error Responses

All error responses follow [RFC 7807 Problem Detail](https://www.rfc-editor.org/rfc/rfc7807) format:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Order d290f1ee-6c54-4b01-90e6-d701748f0851 not found",
  "instance": "/api/v1/orders/d290f1ee-6c54-4b01-90e6-d701748f0851"
}
```

### Common Status Codes

| Code | Meaning |
|------|---------|
| `200` | Success |
| `201` | Resource created |
| `400` | Invalid request body or parameters |
| `401` | Missing or invalid JWT token |
| `404` | Resource not found |
| `429` | Rate limit exceeded |
| `500` | Internal server error |
