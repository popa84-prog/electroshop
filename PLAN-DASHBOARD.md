# Plan de lucru — Redesign complet Dashboard administrare (Task 1–20)

Documentul acesta este contractul de execuție. Fixează, înainte de orice linie de
cod, patru lucruri: ce date există deja și pot fi calculate imediat, ce date
lipsesc și trebuie mai întâi colectate, în ce ordine se construiesc fișierele
astfel încât fiecare val să se sprijine pe unul deja complet, și care sunt
deciziile tehnice care nu se renegociază pe parcurs.

---

## 1. Ce am găsit în cod (analiza care determină planul)

### 1.1 Stack real

| Strat | Tehnologie constatată |
|---|---|
| Backend | Spring Boot, Spring Data JPA, Spring Security cu JWT |
| Persistență | MySQL în producție (`ddl-auto=update`), H2 în teste (`create-drop`) |
| Autorizare | `Permission` enum + `RolePermissions` + `@PreAuthorize("@permissionService.has('…')")` |
| Frontend | React 18, Vite 5, React Router 6, Tailwind 3, Recharts 2.12, Axios |
| Design system | `frontend/src/components/xxii/` — 21 componente, glassmorphism deja implementat |

**Consecință directă asupra enunțului task-urilor.** Task 10 cere „integrează în
Prisma/ORM". Proiectul nu folosește Prisma; folosește Spring Data JPA. Cerința se
execută în ORM-ul real al proiectului, prin query-uri de agregare JPQL. A
introduce Prisma ar însemna un al doilea strat de acces la date peste aceeași
bază — decizie respinsă.

### 1.2 Date care există deja și susțin calculele cerute

Această secțiune este cea mai importantă din întreaga analiză, pentru că
stabilește care task-uri sunt pur calcul și care task-uri au nevoie întâi de
colectare de date.

| Câmp existent | Entitate | Ce task deblochează |
|---|---|---|
| `purchasePrice` | `Product` | **T10** valoare stoc, **T11** profit potențial, **T12**, **T13** |
| `price`, `stockQuantity` | `Product` | **T9**, **T10**, **T11**, **T13** |
| `category`, `subcategory`, `brand` | `Product` | **T12** breakdown, **T6** filtre, **T18** |
| `costPrice` | `OrderItem` | **profit real realizat** — T12, T14, T18 |
| `unitPrice`, `quantity` | `OrderItem` | **T6**, **T12**, **T14**, **T18** |
| `status`, `totalAmount`, `createdAt` | `Order` | **T14**, **T15** parțial, **T16** |
| `user` | `Order` | **T16** clienți noi vs recurenți, valoare medie coș |
| `actor`, `action`, `entityType`, `entityId`, `details` | `AuditLog` | **T5** audit trail |
| `LoginEvent` | entitate proprie | **T16** parțial, **T19** securitate |
| `Purchase`, `PurchaseItem`, `Supplier` | entități proprii | **T13** recomandări aprovizionare |
| `Offer` (cu `startsAt`, `endsAt`, `active`, `placement`) | entitate proprie | **T17** parțial |
| `Notification` | entitate proprie | **T8** notificări sistem |

`OrderItem.costPrice` este descoperirea decisivă: costul real al mărfii vândute
este deja înregistrat pe fiecare linie de comandă, la momentul vânzării. Profitul
realizat nu trebuie estimat — se calculează exact, iar T12, T14 și T18 lucrează
pe cifre reale, nu pe aproximări.

### 1.3 Date care lipsesc — și de aceea se colectează întâi

Patru task-uri cer indicatori pentru care baza de date nu conține nicio
înregistrare. Aceștia nu se pot inventa. Pentru fiecare, planul creează întâi
mecanismul de colectare, apoi calculul.

| Task | Indicator cerut | De ce nu e calculabil azi | Ce se creează |
|---|---|---|---|
| **T15** | timp mediu procesare, timp mediu livrare | `Order` are doar `createdAt`/`updatedAt`; tranzițiile de status nu se păstrează | entitatea `OrderStatusEvent` + listener pe `OrderService` |
| **T15** | rata retururi | `OrderStatus` are PENDING, PAID, SHIPPED, DELIVERED, CANCELLED — retur nu există | status nou `RETURNED` + motiv pe `OrderStatusEvent` |
| **T17** | conversii, CTR, cost per achiziție | zero impresii, zero click-uri, zero cost campanie | entitatea `OfferEvent` + câmp `campaignCost` pe `Offer` + endpoint public de tracking |
| **T19** | erori API, erori cron, erori DB, uptime | nicio eroare nu se persistă | entitatea `SystemLogEntry` + `ApiMetricsFilter` + recorder global de excepții |

**Consecință onestă asupra T17.** Din momentul în care instrumentarea intră în
producție, panoul de marketing începe să acumuleze date. În prima zi va arăta
zero pe toate seriile, pentru că nu există istoric retroactiv de impresii. Panoul
va afișa explicit „colectarea a început la <dată>" în loc să simuleze cifre.
Alternativa — a genera date sintetice ca panoul să arate populat — este respinsă:
un panou de business care minte este mai rău decât un panou gol.

### 1.4 Coliziuni de rutare verificate

Enunțul cere `/api/orders/efficiency` și `/api/products/performance`. Ambele
prefixe sunt deja ocupate: `OrderController` mapează `/orders` cu `@GetMapping("/{id}")`,
`ProductController` mapează `/products` cu același tipar. Spring rezolvă
`RequestMappingHandlerMapping` alegând potrivirea cea mai specifică între toate
controllerele, iar un segment literal bate întotdeauna un segment-șablon. Rutele
cerute funcționează exact ca în enunț, declarate în controllere dedicate. Nu se
schimbă niciun path public.

### 1.5 Ce se poate refolosi (și de aceea nu se rescrie)

- Glassmorphism: `GlassPanel.jsx` îl implementează deja. Se extinde, nu se înlocuiește.
- Grafice: `ChartTheme.jsx` definește deja paleta de serii, axele, grila,
  tooltipul și filtrele de glow pentru Recharts. Toate graficele noi consumă
  acest fișier, deci întregul dashboard rămâne un singur sistem vizual.
- Iconografie: `AdminNav.jsx` conține un set SVG inline scris de mână, cu un
  comentariu explicit că proiectul nu depinde de un pachet de iconițe.
- Sortare/animație: `Reveal.jsx`, `TiltCard.jsx`, `StatTile.jsx` există deja.

---

## 2. Decizii tehnice fixate înainte de implementare

**D1 — Iconografie: `lucide-react` se adaugă ca dependență.** Task 1 și Task 9 o
cer nominal. Pachetul este tree-shakeable, nu are peer dependencies conflictuale
cu React 18 și adaugă doar iconițele importate efectiv. Setul SVG existent din
`AdminNav.jsx` rămâne pe loc pentru rail-ul de navigare, ca schimbarea să nu
atingă o zonă care funcționează.

**D2 — Drag-and-drop: fără dependență externă.** Task 4 cere mutarea cardurilor.
`@dnd-kit` sau `react-beautiful-dnd` ar rezolva problema, dar frontend-ul se
construiește pe Vercel, iar un pachet care eșuează la instalare nu strică un
panou — strică tot panoul de administrare. Drag-and-drop-ul se implementează în
`useCardDrag.js` peste HTML5 Drag & Drop API plus Pointer Events pentru touch,
cu suport de tastatură. Zero risc de build, control total asupra
accesibilității.

**D3 — Grafice: Recharts, deja prezent.** Task 2 acceptă „Recharts / Nivo /
ECharts". Recharts este deja instalat, deja tematizat prin `ChartTheme.jsx` și
deja folosit în dashboard-ul curent. A migra la Nivo sau ECharts ar însemna o
rescriere completă a temei vizuale fără niciun câștig funcțional.

**D4 — AI: motor determinist de reguli, cu port opțional pentru LLM.** Task 7 și
Task 18 cer „recomandări AI". Proiectul nu are nicio cheie de API pentru un
furnizor LLM și nu voi introduce una. Se construiește `AiInsightService` ca motor
de reguli explicabil peste datele reale — produse cu vânzări în scădere, stoc
imobilizat, marjă sub prag, oportunități de promoție — fiecare sugestie
însoțită de cifrele care au generat-o. Interfața `AiTextGenerator` rămâne ca port
(Hexagonal Architecture, Ports & Adapters): dacă se configurează ulterior un
furnizor, generarea de descrieri de produs se activează prin adăugarea unui
adapter, fără modificări în restul sistemului. Fără furnizor configurat,
endpointul de generare descrieri răspunde cu un șablon derivat din atributele
reale ale produsului și marchează explicit sursa.

**D5 — Migrare bază de date.** `ddl-auto=update` creează tabelele noi automat în
producție. Toate entitățile noi sunt aditive: nicio coloană existentă nu se
redenumește, nicio constrângere nu se strânge. Riscul de pierdere de date este
zero. Se livrează în plus `schema-dashboard.sql` — DDL-ul explicit al tabelelor
noi, cu indexurile aferente — pentru mediile unde `ddl-auto` este `validate`.

**D6 — Indexuri.** Fiecare tabel nou primește indexurile pe care le cer
interogările lui: `order_status_event(order_id, created_at)`,
`offer_event(offer_id, type, created_at)`, `system_log_entry(source, level, created_at)`,
`admin_preference(admin_id, pref_key)` unic, `admin_note(admin_id, kind, done)`.

**D7 — Permisiuni noi.** Se adaugă `METRICS_VIEW`, `SYSTEM_MONITOR`,
`MARKETING_VIEW`, `TOOLS_USE`. Fiecare endpoint nou este protejat prin
`@PreAuthorize`. Adminul le primește pe toate; Managerul primește `METRICS_VIEW`,
`MARKETING_VIEW` și `TOOLS_USE`, dar nu `SYSTEM_MONITOR` — monitorizarea
infrastructurii și Backup & Restore rămân strict la Admin.

**D8 — Backup & Restore (T8).** Endpointul expune exportul metadatelor și
declanșarea unui export logic al catalogului și comenzilor, prin serviciile de
export deja existente (`ProductExportService`, `OrderExportService`,
`AuditLogExportService`). Restaurarea nu se execută din panoul web: o restaurare
de bază de date declanșată dintr-un browser este o operațiune ireversibilă cu
suprafață de atac inacceptabilă. Panoul afișează instrucțiunile și starea
ultimului export.

**D9 — Persistare layout (T4), favorite (T3), unelte (T20).** Trei task-uri cer
persistență per-administrator. În loc de cinci tabele, se folosesc două:
`AdminPreference` (cheie/valoare JSON — layout dashboard, favorite, mod compact)
și `AdminNote` (rânduri structurate cu discriminator `kind` — NOTE, REMINDER,
TASK). Mai puține tabele, aceleași funcționalități, un singur loc de auditat.

**D10 — Performanță.** Toate metricile se calculează prin agregare în baza de
date (`SUM`, `COUNT`, `GROUP BY` în JPQL), niciodată prin încărcarea entităților
în memorie. Rezultatele panourilor scumpe (`profit-breakdown`, `financial/overview`,
`customers/insights`) se memorează în cache Caffeine cu TTL de 60 de secunde,
invalidat la scriere pe produse și comenzi.

---

## 3. Ordinea de lucru — șapte valuri

Ordinea nu este arbitrară. Fiecare val livrează un strat complet și stabil pe
care valul următor se poate sprijini fără să revină asupra lui.

### Wave 0 — Fundament
Entitățile noi, enum-urile, repository-urile, permisiunile și **toate** DTO-urile
de răspuns. Niciun pic de logică de business. La finalul valului, contractul
dintre backend și frontend este complet și înghețat, deci valurile 1–3 și 4–6 pot
fi construite fără să se aștepte unele pe altele.

### Wave 1 — Metrici financiare (T9, T10, T11, T12, T14)
Calculele care se sprijină exclusiv pe `Product.purchasePrice` și
`OrderItem.costPrice`. Sunt cele mai valoroase pentru business și cele mai simple
tehnic, deci intră primele.

### Wave 2 — Operațional (T6, T13, T15, T16, T18)
Sănătatea inventarului, eficiența comenzilor (inclusiv listenerul care începe să
colecteze tranzițiile de status), analiza clienților și performanța produselor.

### Wave 3 — Sistem, audit, marketing, AI, unelte (T5, T7, T8, T17, T19, T20)
Filtrul de metrici API, persistarea logurilor, audit trail-ul extins cu export
CSV, instrumentarea ofertelor, motorul de recomandări și uneltele de
productivitate.

### Wave 4 — Kit frontend (T1, T2, T3, T4)
Componentele de design, hook-urile, serviciile API și animațiile. Niciun panou
încă — doar vocabularul din care se construiesc toate panourile.

### Wave 5 — Panourile dashboard (T1, T2, T5–T9, T12–T20)
Motorul de grid cu drag-and-drop, registrul de panouri și cele 17 panouri.

### Wave 6 — Sidebar, breadcrumbs, căutare globală (T3)
Ultimul val de UI, pentru că modifică `AdminLayout.jsx` — fișierul prin care trec
toate paginile de administrare.

### Wave 7 — Testare și livrare
Teste JUnit, compilare offline, verificare sintaxă JSX, commit local, deploy prin
tehnica de patch în browser, verificare în producție endpoint cu endpoint.

---

## 4. Inventarul complet de fișiere

### 4.1 Backend — fișiere noi (77)

**Entități și enum-uri** — `backend/src/main/java/com/electroshop/model/`

| # | Fișier | Task |
|---|---|---|
| 1 | `OrderStatusEvent.java` | T15 |
| 2 | `OfferEvent.java` | T17 |
| 3 | `OfferEventType.java` | T17 |
| 4 | `SystemLogEntry.java` | T19 |
| 5 | `SystemLogLevel.java` | T19 |
| 6 | `SystemLogSource.java` | T19 |
| 7 | `AdminPreference.java` | T3, T4 |
| 8 | `AdminNote.java` | T20 |
| 9 | `AdminNoteKind.java` | T20 |

**Repository-uri** — `backend/src/main/java/com/electroshop/repository/`

| # | Fișier | Task |
|---|---|---|
| 10 | `OrderStatusEventRepository.java` | T15 |
| 11 | `OfferEventRepository.java` | T17 |
| 12 | `SystemLogRepository.java` | T19 |
| 13 | `AdminPreferenceRepository.java` | T3, T4 |
| 14 | `AdminNoteRepository.java` | T20 |

**DTO-uri** — `backend/src/main/java/com/electroshop/dto/`

| # | Fișier | Task |
|---|---|---|
| 15 | `StockValueDto.java` | T10 |
| 16 | `ProfitPotentialDto.java` | T11 |
| 17 | `BusinessBannerDto.java` | T9 |
| 18 | `ProfitBreakdownDto.java` | T12 |
| 19 | `InventoryHealthDto.java` | T13 |
| 20 | `FinancialOverviewDto.java` | T14 |
| 21 | `OrderEfficiencyDto.java` | T15 |
| 22 | `CustomerInsightsDto.java` | T16 |
| 23 | `MarketingPerformanceDto.java` | T17 |
| 24 | `ProductPerformanceDto.java` | T18 |
| 25 | `SystemLogsDto.java` | T19 |
| 26 | `AdminToolsDto.java` | T20 |
| 27 | `HealthStatusDto.java` | T2, T8 |
| 28 | `PredictiveSalesDto.java` | T2 |
| 29 | `DashboardLayoutDto.java` | T4 |
| 30 | `GlobalSearchDto.java` | T3 |
| 31 | `ActivityFeedDto.java` | T5 |
| 32 | `TopProductsInsightDto.java` | T6 |
| 33 | `AiInsightsDto.java` | T7, T18 |
| 34 | `SystemOverviewDto.java` | T8 |
| 35 | `FavoritesDto.java` | T3 |
| 36 | `AdminNoteDto.java` | T20 |
| 37 | `SeriesPointDto.java` | comun tuturor graficelor |
| 38 | `DeltaDto.java` | T2 — variație față de perioada anterioară |

**Servicii** — `backend/src/main/java/com/electroshop/service/`

| # | Fișier | Task |
|---|---|---|
| 39 | `MetricRange.java` | comun (24h/7d/30d/3m/6m/12m) |
| 40 | `MetricsService.java` | T9, T10, T11 |
| 41 | `ProfitAnalyticsService.java` | T12 |
| 42 | `FinancialOverviewService.java` | T14 |
| 43 | `InventoryHealthService.java` | T13 |
| 44 | `OrderEfficiencyService.java` | T15 |
| 45 | `CustomerInsightsService.java` | T16 |
| 46 | `ProductPerformanceService.java` | T18 |
| 47 | `TopProductsInsightService.java` | T6 |
| 48 | `MarketingPerformanceService.java` | T17 |
| 49 | `SystemLogService.java` | T19 |
| 50 | `HealthMetricsService.java` | T2, T8 |
| 51 | `PredictiveSalesService.java` | T2 |
| 52 | `ActivityFeedService.java` | T5 |
| 53 | `AiInsightService.java` | T7, T18 |
| 54 | `AiTextGenerator.java` | T7 — port (interfață) |
| 55 | `TemplateAiTextGenerator.java` | T7 — adapter implicit |
| 56 | `AdminToolsService.java` | T20 |
| 57 | `AdminPreferenceService.java` | T3, T4 |
| 58 | `GlobalSearchService.java` | T3 |
| 59 | `BackupService.java` | T8 |
| 60 | `SystemLogExportService.java` | T19 — export CSV |

**Controllere** — `backend/src/main/java/com/electroshop/controller/`

| # | Fișier | Rute | Task |
|---|---|---|---|
| 61 | `MetricsController.java` | `/metrics/stock-value`, `/metrics/profit-potential`, `/metrics/banner`, `/metrics/profit-breakdown` | T9–T12 |
| 62 | `FinancialController.java` | `/financial/overview` | T14 |
| 63 | `InventoryController.java` | `/inventory/health` | T13 |
| 64 | `OrderAnalyticsController.java` | `/orders/efficiency` | T15 |
| 65 | `CustomerAnalyticsController.java` | `/customers/insights` | T16 |
| 66 | `ProductAnalyticsController.java` | `/products/performance`, `/products/top-insights` | T18, T6 |
| 67 | `MarketingController.java` | `/marketing/performance`, `/marketing/track` | T17 |
| 68 | `SystemController.java` | `/system/logs`, `/system/overview`, `/system/health-status`, `/system/backup` | T8, T19, T2 |
| 69 | `AdminToolsController.java` | `/admin/tools` | T20 |
| 70 | `DashboardConfigController.java` | `/admin/dashboard/layout`, `/admin/favorites`, `/admin/search` | T3, T4 |
| 71 | `AiAssistantController.java` | `/admin/ai/insights`, `/admin/ai/describe` | T7 |

**Infrastructură** — `backend/src/main/java/com/electroshop/config/`, `.../security/`

| # | Fișier | Task |
|---|---|---|
| 72 | `ApiMetricsFilter.java` | T2, T8, T19 — latență și erori per endpoint |
| 73 | `ApiMetricsRegistry.java` | T2, T8 — contoare în memorie |
| 74 | `SystemErrorRecorder.java` | T19 — persistă excepțiile |
| 75 | `OrderStatusEventListener.java` | T15 — captează tranzițiile |
| 76 | `DashboardCacheConfig.java` | D10 — Caffeine, TTL 60s |
| 77 | `schema-dashboard.sql` (`resources/`) | D5 — DDL explicit + indexuri |

### 4.2 Backend — fișiere modificate (7)

| # | Fișier | Modificare |
|---|---|---|
| 78 | `security/Permission.java` | patru permisiuni noi |
| 79 | `security/RolePermissions.java` | alocarea lor pe roluri |
| 80 | `model/OrderStatus.java` | status `RETURNED` |
| 81 | `service/OrderService.java` | emite `OrderStatusEvent` la fiecare tranziție |
| 82 | `service/OfferService.java` | înregistrează `OfferEvent` |
| 83 | `model/Offer.java` | câmp `campaignCost` |
| 84 | `pom.xml` | dependența Caffeine |

### 4.3 Backend — teste (12)

| # | Fișier |
|---|---|
| 85 | `MetricRangeTest.java` |
| 86 | `MetricsServiceTest.java` |
| 87 | `ProfitAnalyticsServiceTest.java` |
| 88 | `FinancialOverviewServiceTest.java` |
| 89 | `InventoryHealthServiceTest.java` |
| 90 | `OrderEfficiencyServiceTest.java` |
| 91 | `CustomerInsightsServiceTest.java` |
| 92 | `ProductPerformanceServiceTest.java` |
| 93 | `MarketingPerformanceServiceTest.java` |
| 94 | `SystemLogServiceTest.java` |
| 95 | `AdminPreferenceServiceTest.java` |
| 96 | `AiInsightServiceTest.java` |

### 4.4 Frontend — fișiere noi (45)

**Design system** — `frontend/src/components/xxii/`

| # | Fișier | Task |
|---|---|---|
| 97 | `DashCard.jsx` | T1, T4 — cardul modular (glass, header, acțiuni, compact/extins) |
| 98 | `CountUp.jsx` | T9 |
| 99 | `RangeSwitch.jsx` | T2, T14 |
| 100 | `TrendPill.jsx` | T2 |
| 101 | `DataTable.jsx` | T13, T15, T17, T19 |
| 102 | `SearchField.jsx` | T3 |
| 103 | `Breadcrumbs.jsx` | T3 |
| 104 | `ExportButton.jsx` | T5, T19 |
| 105 | `AdvancedTooltip.jsx` | T2, T12, T14 |
| 106 | `EmptyState.jsx` | comun |
| 107 | `SkeletonCard.jsx` | T1 |
| 108 | `SeverityBadge.jsx` | T13, T18, T19 |
| 109 | `LucideIcon.jsx` | T1, T9 |

**Hooks** — `frontend/src/hooks/`

| # | Fișier | Task |
|---|---|---|
| 110 | `useDashboardLayout.js` | T4 |
| 111 | `useCardDrag.js` | T4 — DnD nativ, fără dependență |
| 112 | `useCountUp.js` | T9 |
| 113 | `useMetricRange.js` | T2 |
| 114 | `useFavorites.js` | T3 |
| 115 | `useGlobalSearch.js` | T3 |
| 116 | `useAdminTools.js` | T20 |
| 117 | `useBreadcrumbs.js` | T3 |
| 118 | `usePanelData.js` | comun — fetch + cache + abort |

**Servicii API** — `frontend/src/api/`

| # | Fișier | Task |
|---|---|---|
| 119 | `metricsService.js` | T9–T12, T14 |
| 120 | `analyticsService.js` | T13, T15, T16, T18, T6 |
| 121 | `systemService.js` | T8, T19, T2 |
| 122 | `adminToolsService.js` | T20 |
| 123 | `dashboardConfigService.js` | T3, T4 |
| 124 | `aiService.js` | T7 |

**Panouri** — `frontend/src/pages/admin/dashboard/`

| # | Fișier | Task |
|---|---|---|
| 125 | `PanelRegistry.js` | T1, T4 — id, titlu, span implicit, permisiune |
| 126 | `DashboardGrid.jsx` | T1, T4 |
| 127 | `DashboardHeader.jsx` | T1 — shortcut-uri Produse/Comenzi/Promoții |
| 128 | `LayoutToolbar.jsx` | T1, T4 — compact/extins, ascundere, reset |
| 129 | `BusinessBanner.jsx` | T9 |
| 130 | `SalesChartPanel.jsx` | T2 |
| 131 | `HealthStatusPanel.jsx` | T2 |
| 132 | `PredictiveSalesPanel.jsx` | T2 |
| 133 | `ProfitBreakdownPanel.jsx` | T12 |
| 134 | `InventoryHealthPanel.jsx` | T13 |
| 135 | `FinancialOverviewPanel.jsx` | T14 |
| 136 | `OrderEfficiencyPanel.jsx` | T15 |
| 137 | `CustomerInsightsPanel.jsx` | T16 |
| 138 | `MarketingPerformancePanel.jsx` | T17 |
| 139 | `ProductPerformancePanel.jsx` | T18 |
| 140 | `OperationalLogsPanel.jsx` | T19 |
| 141 | `AdminToolsPanel.jsx` | T20 |

### 4.5 Frontend — fișiere modificate (9)

| # | Fișier | Modificare |
|---|---|---|
| 142 | `pages/admin/AdminDashboard.jsx` | rescriere completă pe motorul de grid |
| 143 | `components/AdminLayout.jsx` | sidebar collapsible, icon-only, breadcrumbs, căutare |
| 144 | `components/AdminNav.jsx` | secțiunea Favorite, mod icon-only |
| 145 | `components/xxii/index.js` | barrel-ul cu componentele noi |
| 146 | `components/xxii/ChartTheme.jsx` | tooltip avansat cu delta |
| 147 | `index.css` | animații de intrare a cardurilor, stări de drag |
| 148 | `tailwind.config.js` | keyframes și durate noi |
| 149 | `package.json` | `lucide-react` |
| 150 | `utils/format.js` | formatare monetară și procentuală pentru metrici |

**Total: 150 de fișiere — 96 backend, 54 frontend.**

---

## 5. Panourile care înlocuiesc cardurile vechi (T9)

Cardurile actuale „Utilizatori / Produse / Comenzi / Venit total" se elimină din
`AdminDashboard.jsx`. Cele patru carduri noi și formulele exacte:

| Card | Formulă | Sursă |
|---|---|---|
| Valoare totală stoc | `SUM(purchasePrice × stockQuantity)` peste produsele active | `Product` |
| Profit potențial | `SUM((price − purchasePrice) × stockQuantity)` peste produsele active cu `purchasePrice` nenul | `Product` |
| Vânzări luna curentă | `SUM(totalAmount)` peste comenzile din luna curentă cu status ≠ CANCELLED | `Order` |
| Marjă medie | `(SUM((price − purchasePrice) × stockQuantity) / SUM(price × stockQuantity)) × 100` | `Product` |

Produsele fără `purchasePrice` se exclud din numărător **și** din numitor la
marjă, și se raportează separat ca `productsWithoutCost`, astfel încât cifra
afișată să nu fie diluată de necunoscut. Cardul afișează contorul acestora ca
avertisment, pentru că un catalog cu prețuri de intrare incomplete produce o
marjă optimistă, iar operatorul trebuie să știe asta.

---

## 6. Criterii de acceptanță

Fiecare val se consideră încheiat doar când toate condițiile de mai jos sunt
îndeplinite.

1. Compilare fără erori și fără avertismente noi în harness-ul offline.
2. Toate testele existente trec — cele 67 actuale rămân verzi.
3. Fiecare serviciu nou are cel puțin un test care verifică o valoare calculată
   pe date construite manual, nu doar că metoda nu aruncă excepție.
4. Fiecare endpoint nou este protejat prin `@PreAuthorize` cu o permisiune
   explicită.
5. Niciun panou nu afișează date inventate: dacă sursa este goală, panoul spune
   că este goală.
6. Fiecare panou funcționează la lățime de 360 px fără scroll orizontal.
7. Fiecare grafic are legendă când are două sau mai multe serii, tooltip la
   hover și o alternativă textuală accesibilă.

---

## 7. Riscuri și cum sunt tratate

| Risc | Tratament |
|---|---|
| `lucide-react` eșuează la build pe Vercel | Import direct din `lucide-react/icons/*`; `LucideIcon.jsx` are fallback pe setul SVG existent, deci panoul rămâne funcțional |
| `ddl-auto=update` nu creează un tabel nou | `schema-dashboard.sql` livrat ca DDL explicit |
| Interogări lente pe cataloage mari | Agregare în DB, indexuri dedicate, cache Caffeine 60s |
| Panoul de marketing arată zero la lansare | Panoul afișează data începerii colectării, nu cifre simulate |
| `AdminLayout.jsx` modificat afectează toate paginile | Ultimul val, după ce restul este stabil și verificabil |
| Deploy prin browser cu fișiere multe | Patch comprimat, verificare SHA-256 înainte de commit, fișier cu fișier |

---

## 8. Regula de execuție

Conform instrucțiunii permanente: **se creează întâi toate fișierele pentru toate
task-urile, local. Abia la final se face upload și testare în producție.** Niciun
deploy parțial, nicio verificare pe producție înainte ca inventarul de 150 de
fișiere să fie complet și compilat local.
