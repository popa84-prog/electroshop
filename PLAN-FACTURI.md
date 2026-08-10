# Modul de facturare — plan de execuție

Contractul acestei lucrări. Deciziile din secțiunea D sunt luate; codul le
implementează, nu le renegociază. Inventarul din secțiunea F este lista completă
de fișiere: nimic în afara ei nu se atinge.

Pachetele aprobate: **fundament plus pagina Facturi** și **anulare cu storno și
retur parțial**. Plățile, proformele, e-Factura ANAF și trimiterea pe email sunt
în afara acestei lucrări; entitățile sunt însă proiectate ca să le suporte fără
migrare distructivă.

---

## A. De ce nu este suficientă starea actuală

Factura de astăzi nu este un document. Sunt trei coloane pe `orders` —
`invoice_series`, `invoice_number`, `invoice_issued_at` — plus un PDF regenerat
la cerere din comanda vie.

Din asta decurg două defecte care nu se pot repara fără o entitate separată.

**PDF-ul se rescrie retroactiv.** `InvoiceService.buildPdf` citește numele
produsului, prețul unitar, datele firmei și cota de TVA din starea curentă a
bazei. Redenumești un produs, îi schimbi prețul, muți sediul firmei sau treci de
la neplătitor la plătitor de TVA, și factura emisă acum șase luni se retipărește
altfel decât a fost trimisă clientului. Documentul pe care îl are cumpărătorul și
documentul pe care îl are magazinul încetează să coincidă, iar la un control
diferența nu se poate explica.

**Numărul se alocă la descărcare, nu la emitere.** În `generateForOrder`, blocul
`if (order.getInvoiceNumber() == null)` ia următorul număr din
`CompanySettings.invoiceNextNumber` și incrementează contorul. Efectul este că
numerele ies în ordinea în care cineva a apăsat butonul de descărcare, nu în
ordinea emiterii, iar un clic pe o comandă neplătită consumă definitiv un număr
fiscal. Dacă acea comandă este apoi ștearsă, numărul rămâne o gaură în serie
pentru care nu există document.

## B. Ce există deja și trebuie păstrat

`OrderService.updateStatus` readuce stocul la trecerea pe `CANCELLED`,
parcurgând liniile comenzii și sărind peste produsele șterse definitiv
(`item.getProduct() == null`). Comportamentul este corect și rămâne. Problema
este că devine a doua cale de restituire, alături de storno.

`CompanySettings` conține deja seria, următorul număr, cota de TVA, indicatorul
de plătitor de TVA, datele de identificare ale firmei și mențiunile de pe
factură. Nu se adaugă coloane acolo.

`OrderStatusRecorder` înregistrează tranzițiile de status. Stornarea nu schimbă
statusul comenzii, deci nu trece prin el.

## C. Capcana centrală: dubla restituire a stocului

După implementare vor exista două acțiuni care readuc marfa în stoc: anularea
comenzii și stornarea facturii. Un operator care storneaza factura și apoi trece
comanda pe anulată — o secvență absolut firească — ar adăuga cantitățile de două
ori. Stocul ar crește cu marfă care nu există fizic, iar valoarea de inventar,
profitul potențial și indicatorul de stoc critic ar deveni false toate deodată.

Soluția nu este să interzicem una dintre căi. Este să facem restituirea
**idempotentă la nivel de linie de comandă**.

`OrderItem` primește coloana `restocked_quantity`, implicit `0`, care spune câte
bucăți din acea linie s-au întors deja în stoc. Orice restituire trece printr-un
singur serviciu, care pentru fiecare linie calculează:

```
disponibil    = quantity - restockedQuantity
de_restituit  = min(disponibil, cerut)
```

adaugă `de_restituit` în stocul produsului și incrementează `restockedQuantity`
cu aceeași valoare. Când `disponibil` este zero, nu se întâmplă nimic.

Consecințele sunt exact cele dorite. Storno total urmat de anulare: a doua
operație găsește `disponibil = 0` și nu mișcă nimic. Anulare urmată de storno:
identic. Storno parțial de 2 bucăți dintr-o linie de 5, urmat de anulare:
restituie 2, apoi restul de 3. Ordinea încetează să conteze, iar suma
restituită nu poate depăși niciodată cantitatea vândută.

## D. Decizii

**D1 — Entitate `Invoice` separată, cu date înghețate.** Factura copiază la
emitere numele și datele fiscale ale vânzătorului și cumpărătorului, cota de
TVA, indicatorul de plătitor, și liniile cu denumire, cantitate și preț unitar.
PDF-ul se generează exclusiv din acest instantaneu. Legătura către `Order`
rămâne, pentru trasabilitate, dar nu mai este sursa datelor tipărite.

**D2 — Serie unică pentru toate documentele.** Facturile și stornările iau
număr din același contor, `CompanySettings.invoiceNextNumber`. Este alegerea
explicită a beneficiarului. `InvoiceType` distinge documentele; numerotarea nu.

**D3 — Emitere explicită.** Numărul se alocă la `POST /admin/invoices`, nu la
descărcare. `GET /admin/invoices/{id}/pdf` doar tipărește ce există deja. Un
`GET` nu mai are voie să modifice starea fiscală.

**D4 — Stornarea nu șterge nimic.** Factura originală rămâne cu numărul ei.
Stornarea creează un document nou, de tip `STORNO`, cu număr propriu din aceeași
serie, cu cantități și valori negative, care referă originalul prin
`original_invoice_id`. Factura originală primește statutul `PARTIALLY_STORNOED`
sau `CANCELLED`, după cât s-a stornat.

**D5 — Stornare parțială pe linii și cantități.** Cererea trimite perechi
`invoiceLineId` și `quantity`. Suma stornată pe o linie nu poate depăși
cantitatea facturată minus cât s-a stornat deja. Încălcarea este respinsă cu
`400`, nu tăiată tăcut la maxim.

**D6 — Motivul este obligatoriu.** Stornarea fără motiv este respinsă. Motivul
se tipărește pe documentul de storno și intră în jurnalul de audit. O stornare
fără explicație este, la un control, o stornare nejustificată.

**D7 — Restituirea stocului este opțională la stornare, implicit activă.**
Cererea are indicatorul `restock`, implicit `true`. Cazul în care marfa nu se
întoarce fizic — produs deteriorat, pierdut la transport — este real, iar a
forța restituirea ar umfla stocul cu marfă inexistentă. Când indicatorul este
`false`, faptul se consemnează în audit.

**D8 — Stornarea nu schimbă statusul comenzii.** Sunt două planuri diferite:
documentul fiscal și starea logistică. Operatorul decide separat dacă respectiva
comandă devine `CANCELLED` sau `RETURNED`. Datorită regulii din secțiunea C,
poate face asta în orice ordine, fără efecte asupra stocului.

**D9 — Permisiuni noi și separate.** `INVOICE_VIEW`, `INVOICE_ISSUE`,
`INVOICE_CANCEL`. Emiterea și stornarea sunt decizii fiscale; cine vede lista nu
are automat dreptul să emită, iar cine emite nu are automat dreptul să storneze.
`ROLE_ADMIN` le primește pe toate prin `EnumSet.allOf`. `ROLE_MANAGER` primește
`INVOICE_VIEW` și `INVOICE_ISSUE`. `ROLE_EDITOR` nu primește niciuna, nici măcar
`INVOICE_VIEW`: registrul adună la un loc identificatorii fiscali ai tuturor
clienților și întregul istoric de facturare, ceea ce este un cerc de încredere
diferit de cel al redactării conținutului de produs. Stornarea rămâne exclusiv la
administrator.

**D10 — Migrare idempotentă a facturilor existente.** Comenzile care au deja
serie și număr primesc rânduri `Invoice` construite din datele lor, fără să se
aloce numere noi și fără să se atingă contorul. Rulează la pornire, verifică
existența înainte de inserare, și la a doua pornire nu face nimic.

**D11 — Rotunjirea TVA se face pe linie, nu pe total.** Fiecare linie își
calculează baza, TVA-ul și totalul cu `RoundingMode.HALF_UP` la două zecimale,
iar totalurile documentului sunt sume de linii. Calculul invers — TVA pe totalul
general — produce diferențe de un ban față de suma liniilor tipărite, iar acea
diferență este exact ce sesizează un contabil.

**D12 — Prețurile stocate includ TVA.** `OrderItem.unitPrice` este prețul de
raft, cu TVA inclus, la fel ca `Product.price`. Factura descompune înapoi:
`baza = brut / (1 + cota/100)`, `tva = brut - baza`. A trata prețul de raft ca
bază ar umfla fiecare factură cu cota de TVA.

## E. Model de date

`invoices` — cheie unică pe perechea `(series, number)`, care face imposibilă
emiterea a două documente cu același număr chiar dacă două cereri ajung
simultan.

| coloană | rol |
|---|---|
| `type` | `INVOICE` sau `STORNO` |
| `status` | `ISSUED`, `PARTIALLY_STORNOED`, `CANCELLED` |
| `order_id` | comanda sursă |
| `original_invoice_id` | doar pe storno: factura stornată |
| `seller_*` | instantaneul datelor firmei la emitere |
| `buyer_*` | instantaneul datelor cumpărătorului |
| `vat_payer`, `vat_rate` | regimul fiscal la emitere |
| `total_net`, `total_vat`, `total_gross` | totaluri, negative pe storno |
| `cancel_reason`, `cancelled_at` | motivul stornării |

`invoice_lines` — `product_id` nullable, ca ștergerea definitivă a unui produs
să nu distrugă documentul; `product_name` și `sku` sunt copii, nu referințe.
`stornoed_quantity` ține evidența cât s-a stornat din fiecare linie.

`order_items.restocked_quantity` — contorul din secțiunea C.

## F. Inventar de fișiere

**Backend, fișiere noi (13):**

```
model/Invoice.java
model/InvoiceLine.java
model/InvoiceType.java
model/InvoiceStatus.java
repository/InvoiceRepository.java
service/OrderRestockService.java
service/InvoiceIssueService.java
service/InvoiceCancellationService.java
service/InvoiceBackfillRunner.java
dto/InvoiceDto.java
dto/InvoiceLineDto.java
dto/InvoiceSummaryDto.java
controller/InvoiceController.java
```

**Backend, fișiere modificate (7):**

```
model/OrderItem.java            + restockedQuantity
service/OrderService.java       deleaga restituirea catre OrderRestockService
service/InvoiceService.java     PDF din instantaneu, nu din comanda vie
service/AuditService.java       currentActorName() expus public
security/Permission.java        + INVOICE_VIEW, INVOICE_ISSUE, INVOICE_CANCEL
security/RolePermissions.java   noile permisiuni in matrice
controller/AdminController.java ruta veche de descarcare, redirectionata
```

**Backend, teste noi (3):**

```
service/OrderRestockServiceTest.java
service/InvoiceIssueServiceTest.java
service/InvoiceCancellationServiceTest.java
```

**Frontend, fișiere noi (3):**

```
api/invoiceService.js
pages/admin/AdminInvoices.jsx
components/admin/StornoDialog.jsx
```

**Frontend, fișiere modificate (3):**

```
App.jsx                         ruta /admin/invoices
components/AdminNav.jsx         intrare de navigatie sub FINANCIAR
pages/admin/AdminOrders.jsx     buton de emitere explicita
```

Total: 29 de fișiere.

## G. Criterii de acceptanță

1. Emiterea a două facturi consecutive produce numere consecutive, fără gol.
2. Descărcarea unui PDF nu modifică niciun contor și nu creează niciun document.
3. Redenumirea unui produs după emitere nu schimbă conținutul PDF-ului emis.
4. Storno total urmat de anularea comenzii restituie stocul o singură dată.
5. Anularea comenzii urmată de storno total restituie stocul o singură dată.
6. Storno parțial de `k` bucăți restituie exact `k`, iar anularea ulterioară
   restituie restul.
7. Stornarea unei cantități mai mari decât cea facturată este respinsă cu `400`.
8. Stornarea fără motiv este respinsă cu `400`.
9. Documentul de storno are valori negative și referă numărul original.
10. Suma `total_net + total_vat` este egală cu `total_gross` pe fiecare document.
11. Un utilizator fără `INVOICE_CANCEL` primește `403` la stornare.
12. Migrarea rulată de două ori produce același număr de rânduri.
