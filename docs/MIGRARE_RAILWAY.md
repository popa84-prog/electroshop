# ElectroShop - Ghid complet de migrare pe host platit (Railway)

Acest ghid muta intreaga aplicatie (backend Spring Boot + baza de date MySQL + frontend React)
de pe stack-ul gratuit actual (Render free + Aiven free + Vercel) pe **Railway**, un serviciu
platit "all-in-one" unde **nu mai apar opririle** (fara spin-down dupa inactivitate, fara
suspendarea bazei de date gratuite).

Timp estimat: 30-45 de minute. Nu este nevoie sa modifici codul - doar configurare.

---

## 0. De ce Railway

| Problema pe stack-ul gratuit | Cum o rezolva Railway (platit) |
|---|---|
| Render free "adoarme" dupa ~15 min -> prima cerere dureaza ~1 min | Instanta mereu pornita (fara cold start) |
| Aiven MySQL gratuit se suspenda / expira -> baza devine inaccesibila | MySQL managed platit, mereu activ, cu backup automat |
| 3 servicii separate (Render + Aiven + Vercel) de administrat | Backend + MySQL (+ optional frontend) in acelasi proiect |

Cost orientativ Railway: plan **Hobby** ~5 USD/luna credit inclus; consumul real pentru
un backend mic + MySQL este de regula in jur de 5-10 USD/luna. Verifica pretul curent pe
railway.com/pricing inainte de a incepe.

---

## 1. Backup complet INAINTE de migrare (obligatoriu)

### 1.1. Codul sursa
Codul complet (frontend + backend + database + docs) este arhivat in
`electroshop_backup_complet.zip`. In plus, tot codul este deja pe GitHub:
`https://github.com/popa84-prog/electroshop`.

### 1.2. Baza de date - snapshot exact al datelor curente (Aiven online)
Cat timp baza Aiven este pornita, fa un dump complet (schema + toate datele: produse,
utilizatori, comenzi). Ruleaza de pe calculatorul tau (ai nevoie de clientul `mysql` /
`mysqldump` instalat). Datele de conexiune sunt cele din Render -> Environment
(`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`) sau din consola Aiven.

```bash
mysqldump \
  --host=<AIVEN_HOST> \
  --port=<AIVEN_PORT> \
  --user=<AIVEN_USER> \
  --password=<AIVEN_PASSWORD> \
  --ssl-mode=REQUIRED \
  --single-transaction --routines --triggers --databases electroshop \
  > electroshop_dump_$(date +%Y%m%d).sql
```

Rezulta un fisier `electroshop_dump_YYYYMMDD.sql` cu TOT (structura + date). Acesta este
backup-ul "oficial" al bazei live.

### 1.3. Backup de rezerva din proiect (daca baza nu e disponibila)
Daca baza Aiven este iar oprita, poti reconstrui integral catalogul din fisierele incluse:
- `database/schema.sql` - schema completa, la zi (toate tabelele).
- `database/03_products_data.sql` - cele 479 de produse (INSERT-uri gata de rulat).
- `database/seed.sql` - roluri + conturi demo.

---

## 2. Creeaza proiectul pe Railway

1. Intra pe `https://railway.com` si autentifica-te (recomandat: "Login with GitHub",
   ca sa poti deploya direct din repo).
2. Apasa **New Project**.
3. Alege **Deploy from GitHub repo** -> autorizeaza accesul -> selecteaza repo-ul
   `popa84-prog/electroshop`.
4. Railway va detecta `backend/Dockerfile`. Daca intreaba directorul radacina al serviciului,
   seteaza **Root Directory = `backend`**.

---

## 3. Adauga baza de date MySQL

1. In proiectul Railway, apasa **New** -> **Database** -> **Add MySQL**.
2. Railway creeaza serviciul MySQL si expune automat variabile precum
   `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`.
3. Noteaza-le (Tab-ul **Variables** al serviciului MySQL) - le folosesti la pasul 4.

---

## 4. Configureaza variabilele de mediu ale backend-ului

Deschide serviciul **backend** -> tab **Variables** -> adauga exact aceste chei
(valorile pentru DB le iei din serviciul MySQL de la pasul 3):

| Cheie | Valoare | Observatii |
|---|---|---|
| `DB_HOST` | `${{MySQL.MYSQLHOST}}` | referinta Railway catre serviciul MySQL |
| `DB_PORT` | `${{MySQL.MYSQLPORT}}` | |
| `DB_NAME` | `${{MySQL.MYSQLDATABASE}}` | |
| `DB_USER` | `${{MySQL.MYSQLUSER}}` | |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` | |
| `JWT_SECRET` | un string base64 de min. 256 biti | genereaza unul nou pentru productie |
| `CORS_ORIGINS` | `https://<domeniul-frontend>` | ex. domeniul Vercel sau cel de pe Railway |
| `OWNER_ADMIN_PASSWORD` | parola ta de admin | pentru contul popa84@icloud.com |
| `CLOUDINARY_CLOUD_NAME` | (cloud name-ul tau) | pentru imaginile produselor (#5) |
| `CLOUDINARY_API_KEY` | (cheia ta de API) | |
| `CLOUDINARY_API_SECRET` | (secretul tau Cloudinary) | tine-l DOAR aici, niciodata in frontend |
| `JAVA_TOOL_OPTIONS` | `-XX:+UseSerialGC -Xmx400m -Xss512k` | optional; ajusteaza la RAM-ul planului |
| `PORT` | `8080` | Railway il injecteaza de obicei automat |

> Nota tehnica: aplicatia construieste URL-ul JDBC din `DB_HOST/DB_PORT/DB_NAME`
> (vezi `application.properties`). Alternativ poti seta direct
> `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

Apasa **Deploy**. La prima pornire, Hibernate creeaza automat toate tabelele
(`spring.jpa.hibernate.ddl-auto=update`) si `DataInitializer` face seed la roluri si
contul de admin.

---

## 5. Incarca datele (produsele)

Ai doua variante - alege una:

**Varianta A (recomandata, cea mai simpla): restaurezi dump-ul complet**
```bash
mysql --host=<MYSQLHOST> --port=<MYSQLPORT> \
      --user=<MYSQLUSER> --password=<MYSQLPASSWORD> \
      <MYSQLDATABASE> < electroshop_dump_YYYYMMDD.sql
```
Aduce inapoi TOT (produse + utilizatori + comenzi) exact ca pe Aiven.

**Varianta B: doar catalogul de produse**
1. Lasa backend-ul sa porneasca (creeaza schema + seed).
2. Incarca produsele din panoul Admin -> **Produse** -> **Import Excel**, cu fisierul
   `ElectroShop_Import_CURATAT.xlsx` (479 produse). Sau ruleaza `database/03_products_data.sql`
   direct in MySQL.

---

## 6. Deploy frontend

Ai doua optiuni:

**Optiunea 1 - pastrezi Vercel (recomandat, e deja configurat):**
- In Vercel -> proiectul ElectroShop -> Settings -> Environment Variables, seteaza
  `VITE_API_URL = https://<backend-railway>.up.railway.app/api`.
- Redeploy. Actualizeaza si `CORS_ORIGINS` pe backend cu domeniul Vercel.

**Optiunea 2 - muti si frontend-ul pe Railway:**
1. New -> GitHub repo -> acelasi repo, **Root Directory = `frontend`** (detecteaza
   `frontend/Dockerfile`).
2. Variabila de build: `VITE_API_URL = https://<backend-railway>.up.railway.app/api`.
3. Deploy. Actualizeaza `CORS_ORIGINS` pe backend cu noul domeniu al frontend-ului.

---

## 7. Verificare finala (checklist)

- [ ] Backend raspunde: `https://<backend>.up.railway.app/api/products?size=1` -> JSON 200.
- [ ] Frontend se incarca si afiseaza produsele.
- [ ] Login cu contul de admin (popa84@icloud.com) functioneaza.
- [ ] Admin -> Produse -> Editeaza -> upload imagine -> apare URL `res.cloudinary.com/...`.
- [ ] Admin -> Date firma -> completezi si salvezi -> ramane salvat dupa refresh.
- [ ] Admin -> Comenzi -> Factura -> se descarca PDF-ul corect.
- [ ] Cos + checkout + plasare comanda functioneaza.

---

## 8. Backup recurent (dupa migrare)

Railway MySQL are backup-uri automate in planurile platite. In plus, poti programa un
`mysqldump` periodic (ex. cron pe calculatorul tau sau un job dedicat):

```bash
mysqldump --host=<MYSQLHOST> --port=<MYSQLPORT> \
          --user=<MYSQLUSER> --password=<MYSQLPASSWORD> \
          --single-transaction --routines --triggers \
          --databases railway > backup_$(date +%Y%m%d_%H%M).sql
```

---

## 9. Rezumatul variabilelor de mediu (toate serviciile)

**Backend (Spring Boot):**
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`,
`OWNER_ADMIN_PASSWORD`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`,
`CLOUDINARY_API_SECRET`, `JAVA_TOOL_OPTIONS`, `PORT`.

**Frontend (Vite/React):**
`VITE_API_URL`.

Vezi si `deploy/.env.example` din arhiva pentru un sablon gata de completat.
