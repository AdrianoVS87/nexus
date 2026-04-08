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

> **Note:** Product and notification endpoints are public. Order endpoints require a valid JWT token.

---

## Orders

### Create Order

```
POST /api/v1/orders
```

Requires: `Authorization: Bearer <token>`

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
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token | jq -r .token)

curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [{
      "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "productName": "Mechanical Keyboard",
      "quantity": 1,
      "unitPrice": 149.99
    }]
  }' | jq .
```

**Response (201 Created):**
```json
{
  "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PAYMENT_REQUESTED",
  "totalAmount": 149.99,
  "currency": "USD",
  "items": [...],
  "createdAt": "2026-04-03T21:00:00Z",
  "updatedAt": "2026-04-03T21:00:00Z"
}
```

**Validation:**
- `userId`: required, UUID
- `items`: non-empty list
- `quantity`: positive integer
- `unitPrice`: >= 0.01

### Get Order

```
GET /api/v1/orders/{id}
```

### Get User Orders

```
GET /api/v1/orders?userId={userId}&page=0&size=20
```

### Cancel Order

```
POST /api/v1/orders/{id}/cancel
```

Requires: `Authorization: Bearer <token>`

Cancels an order and triggers saga compensation (payment refund if applicable).

**Request Body (optional):**
```json
{
  "reason": "Changed my mind"
}
```

**curl:**
```bash
curl -s -X POST http://localhost:8080/api/v1/orders/{ORDER_ID}/cancel \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"reason": "Changed my mind"}' | jq .
```

**Response (200 OK):**
```json
{
  "id": "d290f1ee-...",
  "status": "CANCELLED",
  ...
}
```

**Cancellable statuses:** PENDING, PAYMENT_REQUESTED, CONFIRMED

---

## Products

### List Products

```
GET /api/v1/products
```

Returns all products from the Redis cache (or PostgreSQL on cache miss).

### Search Products

```
GET /api/v1/products/search?name=keyboard&minPrice=50&maxPrice=200&inStock=true&page=0&size=20&sortBy=price
```

All query parameters are optional. Supports:
- `name` — case-insensitive partial match
- `minPrice` / `maxPrice` — price range filter
- `inStock` — `true` to show only available products
- `page`, `size`, `sortBy` — pagination and sorting

### Get Product

```
GET /api/v1/products/{id}
```

### Create Product

```
POST /api/v1/products
```

**Request Body:**
```json
{
  "name": "USB-C Hub",
  "description": "7-in-1, 4K HDMI",
  "price": 49.99,
  "stockQuantity": 200
}
```

### Update Product

```
PUT /api/v1/products/{id}
```

Partial update — only provided fields are changed.

**Request Body:**
```json
{
  "price": 39.99,
  "stockQuantity": 150
}
```

### Delete Product

```
DELETE /api/v1/products/{id}
```

Returns `204 No Content`.

---

## Payments

### Get Payment by Order

```
GET /api/v1/payments/{orderId}
```

---

## Notifications

### Get Notifications by Order

```
GET /api/v1/notifications/{orderId}?page=0&size=20
```

Returns paginated notification history for an order, sorted by most recent first.

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "...",
      "orderId": "d290f1ee-...",
      "userId": "550e8400-...",
      "type": "ORDER_CONFIRMED",
      "channel": "EMAIL",
      "message": "Order confirmation email sent",
      "timestamp": "2026-04-06T10:00:05Z"
    }
  ],
  "totalElements": 4,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

## WebSocket — Real-Time Order Updates

### Connection

```
ws://localhost:8080/ws/orders?orderId=<uuid>
```

The `orderId` query parameter subscribes the connection to updates for that specific order. Without it, all order updates are received (broadcast mode).

**JavaScript:**
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/orders?orderId=d290f1ee-...');
ws.onmessage = (event) => {
  const update = JSON.parse(event.data);
  console.log(`Order ${update.orderId}: ${update.status}`);
};
```

### Message Format

```json
{
  "orderId": "d290f1ee-...",
  "status": "CONFIRMED",
  "message": "Your order has been confirmed",
  "timestamp": "2026-04-03T21:00:05Z"
}
```

**Possible statuses:** `PENDING`, `PAYMENT_REQUESTED`, `PAYMENT_COMPLETED`, `INVENTORY_REQUESTED`, `CONFIRMED`, `CANCELLED`, `REFUND_REQUESTED`

---

## Interactive API Documentation

Each service exposes Swagger UI for interactive API exploration:

| Service | Swagger UI |
|---------|-----------|
| Order Service | `http://localhost:8081/swagger-ui.html` |
| Payment Service | `http://localhost:8082/swagger-ui.html` |
| Inventory Service | `http://localhost:8083/swagger-ui.html` |
| Notification Service | `http://localhost:8084/swagger-ui.html` |

---

## Health Checks

### Gateway Health (aggregated)

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

### Prometheus Metrics

```bash
curl -s http://localhost:8081/actuator/prometheus | grep nexus_
```

Custom business metrics:
- `nexus_orders_created_total` — total orders created
- `nexus_saga_outcome_total{result="confirmed|cancelled"}` — saga completion rate
- `nexus_payment_result_total{result="success|failure"}` — payment success rate
- `nexus_inventory_result_total{result="reserved|insufficient"}` — stock availability

---

## Error Responses

All error responses follow [RFC 7807 Problem Detail](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "type": "https://nexus.com/errors/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "Order d290f1ee-... not found",
  "instance": "/api/v1/orders/d290f1ee-...",
  "timestamp": "2026-04-06T10:00:00Z"
}
```

| Code | Meaning |
|------|---------|
| `200` | Success |
| `201` | Resource created |
| `204` | No content (delete) |
| `400` | Invalid request body or parameters |
| `401` | Missing or invalid JWT token |
| `404` | Resource not found |
| `429` | Rate limit exceeded |
| `500` | Internal server error |
