# Nexus API Reference

Base URL: `http://localhost:8080` (via API Gateway)

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

**Response (201 Created):**
```json
{
  "id": "...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "totalAmount": 149.99,
  "currency": "USD",
  "items": [...],
  "createdAt": "2026-04-03T21:00:00Z",
  "updatedAt": "2026-04-03T21:00:00Z"
}
```

### Get Order

```
GET /api/v1/orders/{id}
```

### Get User Orders

```
GET /api/v1/orders?userId={userId}&page=0&size=20
```

## Products

### List Products

```
GET /api/v1/products
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

## WebSocket

Connect to `ws://localhost:8084/ws/orders` for real-time order status updates.

**Message format:**
```json
{
  "orderId": "...",
  "status": "CONFIRMED"
}
```

## Health Checks

All services expose:
- `GET /actuator/health`
- `GET /actuator/prometheus` (metrics)
