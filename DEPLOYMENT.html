# ElectroShop — Exemple API (request / response)

Toate răspunsurile folosesc wrapper-ul standard:

```json
{
  "success": true,
  "message": "OK",
  "data": { },
  "timestamp": "2026-07-19T12:00:00"
}
```

Base URL: `http://localhost:8080/api`

---

## Flux complet: login → listare produse → adăugare în coș → plasare comandă

### 1. Login

**Request**
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@electroshop.com",
  "password": "user123"
}
```

**Response `200 OK`**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "userId": 2,
    "fullName": "Demo User",
    "email": "user@electroshop.com",
    "roles": ["ROLE_USER"]
  },
  "timestamp": "2026-07-19T12:00:00"
}
```

Salvează `accessToken` și trimite-l în header la apelurile următoare:
```
Authorization: Bearer <accessToken>
```

### 2. Listare produse (public)

**Request**
```http
GET /api/products?page=0&size=12&search=macbook&category=Laptops
```

**Response `200 OK`**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [
      {
        "id": 3,
        "name": "MacBook Air M3",
        "description": "13.6\" Liquid Retina, Apple M3, 16GB RAM, 512GB SSD.",
        "price": 7299.00,
        "stockQuantity": 12,
        "category": "Laptops",
        "brand": "Apple",
        "imageUrl": "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600",
        "createdAt": "2026-07-19T10:00:00"
      }
    ],
    "page": 0,
    "size": 12,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  },
  "timestamp": "2026-07-19T12:00:00"
}
```

### 3. Detalii produs (public)

**Request**
```http
GET /api/products/1
```

**Response `200 OK`**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "name": "iPhone 15 Pro",
    "description": "Apple A17 Pro chip, 6.1\" ProMotion display, titanium frame.",
    "price": 5499.00,
    "stockQuantity": 25,
    "category": "Smartphones",
    "brand": "Apple",
    "imageUrl": "https://images.unsplash.com/photo-1592286927505-1def25115558?w=600",
    "createdAt": "2026-07-19T10:00:00"
  },
  "timestamp": "2026-07-19T12:00:00"
}
```

> Coșul de cumpărături este gestionat pe frontend (Context API + localStorage).
> „Adăugarea în coș” nu necesită apel la server; itemele sunt trimise la checkout.

### 4. Plasare comandă (necesită autentificare)

**Request**
```http
POST /api/orders
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "shippingAddress": "Str. Exemplu 1, Bucuresti",
  "items": [
    { "productId": 1, "quantity": 1 },
    { "productId": 5, "quantity": 2 }
  ]
}
```

**Response `201 Created`**
```json
{
  "success": true,
  "message": "Order placed successfully",
  "data": {
    "id": 6,
    "userId": 2,
    "userFullName": "Demo User",
    "userEmail": "user@electroshop.com",
    "status": "PENDING",
    "totalAmount": 8897.00,
    "shippingAddress": "Str. Exemplu 1, Bucuresti",
    "items": [
      {
        "id": 10,
        "productId": 1,
        "productName": "iPhone 15 Pro",
        "imageUrl": "https://images.unsplash.com/photo-1592286927505-1def25115558?w=600",
        "quantity": 1,
        "unitPrice": 5499.00,
        "subtotal": 5499.00
      },
      {
        "id": 11,
        "productId": 5,
        "productName": "Sony WH-1000XM5",
        "imageUrl": "https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=600",
        "quantity": 2,
        "unitPrice": 1699.00,
        "subtotal": 3398.00
      }
    ],
    "createdAt": "2026-07-19T12:05:00"
  },
  "timestamp": "2026-07-19T12:05:00"
}
```

Stocul produselor este decrementat automat la plasarea comenzii. Dacă stocul este
insuficient, se returnează `400 Bad Request` cu un mesaj explicativ.

---

## Exemple ADMIN

### Creare produs

**Request**
```http
POST /api/products
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{
  "name": "Xiaomi 14",
  "description": "Snapdragon 8 Gen 3, Leica camera.",
  "price": 3999.00,
  "stockQuantity": 20,
  "category": "Smartphones",
  "brand": "Xiaomi",
  "imageUrl": "https://example.com/xiaomi14.jpg"
}
```

**Response `201 Created`** — produsul creat (același format ca detalii produs).

### Upload imagine produs

```http
POST /api/products/1/image
Authorization: Bearer <adminAccessToken>
Content-Type: multipart/form-data

file: <binary image>
```

**Response `200 OK`** — produsul actualizat, `imageUrl` = `/uploads/<uuid>.jpg`
(servit la `GET /api/uploads/<uuid>.jpg`).

### Dashboard statistici

**Request**
```http
GET /api/admin/dashboard
Authorization: Bearer <adminAccessToken>
```

**Response `200 OK`**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "totalUsers": 3,
    "totalProducts": 10,
    "totalOrders": 5,
    "totalRevenue": 33839.00,
    "ordersByStatus": [
      { "status": "DELIVERED", "count": 2 },
      { "status": "SHIPPED", "count": 1 },
      { "status": "PAID", "count": 1 },
      { "status": "PENDING", "count": 1 }
    ],
    "topProducts": [
      { "productId": 8, "name": "Logitech MX Master 3S", "unitsSold": 3, "revenue": 1647.00 }
    ],
    "salesByDay": [
      { "date": "2026-06-01", "amount": 8897.00 }
    ]
  },
  "timestamp": "2026-07-19T12:10:00"
}
```

### Actualizare status comandă

```http
PUT /api/admin/orders/1/status
Authorization: Bearer <adminAccessToken>
Content-Type: application/json

{ "status": "SHIPPED" }
```

---

## Exemple de erori

**Validare `400`**
```json
{
  "success": false,
  "message": "Validation failed",
  "data": { "email": "Email must be valid" },
  "timestamp": "2026-07-19T12:00:00"
}
```

**Credentiale greșite `401`**
```json
{ "success": false, "message": "Invalid email or password", "data": null, "timestamp": "..." }
```

**Fără rol ADMIN `403`**
```json
{ "success": false, "message": "Access denied: insufficient permissions", "data": null, "timestamp": "..." }
```

**Rate limit depășit `429`**
```json
{ "success": false, "message": "Too many requests. Please try again later." }
```
