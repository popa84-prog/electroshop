# ElectroShop — Arhivă backup completă

Acest ZIP conține întregul proiect, gata de rulat sau de migrat pe un host plătit.

## Conținut
- `backend/`   — API Spring Boot (Java 17, Spring Security JWT, JPA), inclusiv `Dockerfile`.
- `frontend/`  — Aplicație React + Vite (TailwindCSS), inclusiv `Dockerfile` și `nginx.conf`.
- `database/`
  - `schema.sql`            — schema MySQL completă, la zi (toate tabelele).
  - `03_products_data.sql`  — cele 479 de produse (INSERT-uri).
  - `seed.sql`              — roluri + conturi demo.
- `docs/`
  - `MIGRARE_RAILWAY.md`    — GHID COMPLET de migrare pe host plătit (Railway).
  - `DEPLOYMENT.html`       — ghid de deploy (varianta Render/Vercel).
  - `API_EXAMPLES.md`       — exemple de request/response API.
- `deploy/.env.example`     — șablon cu toate variabilele de mediu.
- `docker-compose.yml`      — rulare locală completă (MySQL + backend + frontend).
- `render.yaml`             — infra-as-code pentru Render.
- `postman/`                — colecție Postman.

## Rulare locală rapidă (Docker)
```bash
docker compose up --build
# Frontend: http://localhost:5173   Backend: http://localhost:8080/api
```

## Migrare pe host plătit
Urmează `docs/MIGRARE_RAILWAY.md` — pas cu pas, ~30-45 min.

## Cont admin
Email: popa84@icloud.com — parola se setează prin variabila `OWNER_ADMIN_PASSWORD`.
