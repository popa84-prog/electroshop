# ⚡ ElectroShop — Full-Stack E-Commerce (Electronice)

Aplicație completă pentru prezentarea și vânzarea produselor electronice.

- **Backend:** Java 17 · Spring Boot 3 · Spring Security (JWT) · Spring Data JPA
- **Frontend:** React 18 · Vite · React Router 6 · Axios · Context API · TailwindCSS · Recharts
- **Bază de date:** MySQL 8 (H2 in-memory pentru dezvoltare rapidă)
- **Livrare:** Docker Compose (MySQL + backend + frontend)

Aplicația este **responsive** (PC + telefon), are **autentificare cu roluri** (ADMIN / USER),
listare și detalii produse, coș de cumpărături, checkout și plasare comenzi, plus un
**panou de administrare** complet cu CRUD pentru utilizatori, produse și comenzi și un
dashboard cu grafice.

---

## 1. Structura proiectului

```
electroshop/
├── backend/                     # Spring Boot API
│   ├── src/main/java/com/electroshop/
│   │   ├── config/              # Security, CORS, seed data, resource handlers
│   │   ├── controller/          # REST: auth, products, orders, users, admin
│   │   ├── dto/                 # Request/response records + ApiResponse wrapper
│   │   ├── exception/           # Global exception handler
│   │   ├── model/               # JPA entities: User, Role, Product, Order, OrderItem
│   │   ├── repository/          # Spring Data JPA repositories
│   │   ├── security/            # JWT service, filters, rate limiting, UserDetails
│   │   └── service/             # Business logic
│   ├── src/main/resources/application.properties
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                    # React app
│   ├── src/
│   │   ├── api/                 # Axios + service layer (auth/product/order/admin)
│   │   ├── components/          # Navbar, Footer, ProductCard, Modal, guards...
│   │   ├── context/             # AuthContext, CartContext
│   │   ├── pages/               # Home, Products, Cart, Checkout, Orders...
│   │   │   └── admin/           # Dashboard, AdminProducts, AdminUsers, AdminOrders
│   │   └── utils/
│   ├── Dockerfile
│   └── package.json
├── database/
│   ├── schema.sql               # Tabele, chei, indexuri, relații
│   └── seed.sql                 # Date de test (10 produse, 3 useri, 5 comenzi)
├── postman/ElectroShop.postman_collection.json
├── docs/API_EXAMPLES.md         # Exemple request/response + flux complet
└── docker-compose.yml
```

---

## 2. Rulare rapidă cu Docker Compose (recomandat)

Necesită Docker + Docker Compose.

```bash
docker compose up --build
```

- Frontend:  http://localhost:5173
- Backend:   http://localhost:8080/api
- MySQL:     localhost:3306 (user `root` / parolă `root`, baza `electroshop`)

MySQL este inițializat automat cu `database/schema.sql` + `database/seed.sql`.

Oprire: `docker compose down` (adaugă `-v` pentru a șterge și volumul MySQL).

---

## 3. Rulare manuală (dezvoltare)

### 3.1. Backend

**Varianta A — cu MySQL:**

1. Pornește un MySQL local și creează baza (sau lasă `createDatabaseIfNotExist=true` să o creeze):
   ```sql
   SOURCE database/schema.sql;
   SOURCE database/seed.sql;
   ```
2. Ajustează credentialele în `backend/src/main/resources/application.properties`
   (sau prin variabile de mediu `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`).
3. Pornește serverul:
   ```bash
   cd backend
   ./mvnw spring-boot:run        # sau: mvn spring-boot:run
   ```

**Varianta B — fără MySQL (H2 in-memory, cel mai rapid pentru test):**

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

La prima pornire, `DataInitializer` creează automat rolurile, două conturi demo
și câteva produse dacă baza este goală.

Backend disponibil la `http://localhost:8080/api`.

### 3.2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend disponibil la `http://localhost:5173`. Apelurile `/api` sunt proxy-ate
automat către backend (vezi `vite.config.js`).

Build de producție: `npm run build` (rezultatul în `frontend/dist`).

---

## 4. Conturi demo

| Rol   | Email                     | Parolă      |
|-------|---------------------------|-------------|
| ADMIN | admin@electroshop.com     | admin123    |
| USER  | user@electroshop.com      | user123     |
| USER  | customer@electroshop.com  | password123 |

> ADMIN vede meniul **Admin** cu dashboard + management produse/utilizatori/comenzi.

---

## 5. Endpoint-uri principale (context-path `/api`)

| Metodă | Endpoint                         | Acces  | Descriere                       |
|--------|----------------------------------|--------|---------------------------------|
| POST   | `/auth/register`                 | public | Înregistrare                    |
| POST   | `/auth/login`                    | public | Autentificare (returnează JWT)  |
| POST   | `/auth/refresh`                  | public | Reînnoire access token          |
| GET    | `/products`                      | public | Listare + filtrare + paginare   |
| GET    | `/products/{id}`                 | public | Detalii produs                  |
| GET    | `/products/categories`           | public | Categorii distincte             |
| POST   | `/products`                      | ADMIN  | Creare produs                   |
| PUT    | `/products/{id}`                 | ADMIN  | Editare produs                  |
| DELETE | `/products/{id}`                 | ADMIN  | Ștergere produs                 |
| POST   | `/products/{id}/image`           | ADMIN  | Upload imagine produs           |
| POST   | `/orders`                        | USER   | Plasare comandă                 |
| GET    | `/orders`                        | USER   | Comenzile proprii               |
| GET    | `/orders/{id}`                   | USER   | Detalii comandă proprie         |
| GET    | `/users/me`                      | USER   | Profil curent                   |
| GET    | `/admin/dashboard`               | ADMIN  | Statistici + grafice            |
| GET/POST/PUT/DELETE | `/admin/users(/{id})`| ADMIN  | CRUD utilizatori                |
| GET    | `/admin/orders`                  | ADMIN  | Toate comenzile (+ filtru)      |
| PUT    | `/admin/orders/{id}/status`      | ADMIN  | Actualizare status comandă      |
| DELETE | `/admin/orders/{id}`             | ADMIN  | Ștergere comandă                |

Vezi `docs/API_EXAMPLES.md` pentru exemple complete de request/response și fluxul
`login → listare → coș → comandă`.

---

## 6. Securitate

- Parole hash-uite cu **BCrypt** (cost 12).
- **JWT** cu access token (15 min) + refresh token (7 zile); refresh transparent pe frontend.
- **RBAC** — `ROLE_ADMIN` / `ROLE_USER`, verificat atât la nivel de URL cât și cu `@PreAuthorize`.
- **Rate limiting** pe `/auth/**` (Bucket4j) împotriva brute-force.
- **CORS** configurabil, sesiuni **stateless**.
- Validare input (Jakarta Validation) + tratare centralizată a erorilor.
- Protecție **path traversal** la upload de imagini + whitelist tipuri fișier.

---

## 7. Testare

```bash
cd backend
mvn test        # teste unitare (JWT) + test de context Spring pe H2
```

Pentru testare API manuală, importă `postman/ElectroShop.postman_collection.json`
în Postman și rulează întâi **Auth / Login (admin)** (salvează automat token-ul).
