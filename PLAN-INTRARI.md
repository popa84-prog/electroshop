# Intrare de marfă din Excel — plan de execuție

Contractul acestei lucrări. Deciziile din secțiunea D sunt luate; codul le
implementează, nu le renegociază. Inventarul din secțiunea F este lista completă
de fișiere.

Cerința: la încărcarea unui fișier Excel cu produse, sistemul înregistrează
automat intrarea de marfă corespunzătoare, pe baza aceluiași fișier.

---

## A. Capcana care trebuie rezolvată prima: stocul se mișcă de două ori

Astăzi există **două** căi independente prin care marfa intră în stoc, iar
niciuna nu știe de cealaltă.

`ProductImportService` scrie stocul direct. Un produs nou primește
`stockQuantity` din coloana Excel. În modul „intrare marfă" (`restock`),
cantitatea se adaugă la stocul existent și prețul de achiziție se recalculează
ca medie ponderată — logică bună, care există și rămâne.

`PurchaseService.create()` scrie și el stocul: `product.setStockQuantity(stoc +
cantitate)` pentru fiecare linie.

Dacă importul ar crea o achiziție prin serviciul existent, fiecare bucată din
fișier ar intra în stoc **de două ori**. Nu ar apărea nicio eroare: stocul ar
crește cu marfă care nu există, iar valoarea de inventar, profitul potențial și
indicatorul de stoc critic ar deveni false toate deodată. Este exact clasa de
defect rezolvată la stornare, în sens invers.

## B. Un defect existent, descoperit pe drum

`PurchaseService.create()` adaugă stoc, dar **nu atinge niciodată**
`purchasePrice`. O achiziție înregistrată prin pagina Cumpărări la un cost
diferit lasă produsul cu vechiul preț de achiziție. Consecința se vede în
margine și în profitul potențial: catalogul raportează un cost care nu mai
corespunde mărfii de pe raft. Importul face media ponderată corect; pagina de
achiziții nu o face deloc.

Reunificarea celor două căi repară și asta, fără să fie nevoie de o intervenție
separată.

## C. Vocabular: ce se generează, de fapt

**Factura de achiziție nu se generează.** O emite furnizorul, cu seria și
numărul lui. Dacă magazinul ar produce un document numit „factură de intrare"
cu numerotare proprie, ar fabrica documentul altei firme. Câmpul
`Purchase.invoiceNumber` există deja exact pentru numărul furnizorului și
rămâne cu acest rol.

Ce emite magazinul legitim, pe baza mărfii primite, este **NIR-ul — nota de
intrare-recepție** — document intern, cu numerotare proprie, separată de cea a
facturilor de vânzare.

## D. Decizii

**D1 — O singură autoritate pentru intrarea în stoc.** `StockIntakeService`
devine singurul loc care adaugă cantități și recalculează media ponderată a
costului. `ProductImportService` și `PurchaseService` îl apelează; niciunul nu
mai scrie `stockQuantity` direct. Simetric cu `OrderRestockService`, care este
deja singura autoritate pentru ieșire.

**D2 — Intrarea se creează doar la cerere explicită.** Dialogul de import
primește bifa „Aceasta este o intrare de marfă". Fără ea, importul se comportă
exact ca până acum și nu produce niciun document. Un import de corecție a
catalogului care are din întâmplare coloana de stoc completată nu trebuie să
devină o recepție.

**D3 — Furnizorul se alege în dialog, nu din fișier.** O livrare vine de la un
singur furnizor. Alături de el se completează numărul și data facturii lui —
valori care se află pe hârtia primită, nu în fișierul de produse.

**D4 — Un rând fără preț de achiziție respinge întregul fișier.** Cu lista
rândurilor vinovate, identificate prin numărul de rând din Excel și denumire. O
intrare cu valoare zero subevaluează inventarul tăcut, iar diferența se
descoperă peste luni, când nu mai poate fi reconstituită. Corectarea fișierului
costă minute; corectarea inventarului costă un inventar.

**D5 — Același fișier nu poate fi importat de două ori.** Se reține amprenta
SHA-256 a conținutului. O a doua încărcare a aceluiași fișier este respinsă cu
trimitere la recepția deja existentă. Dublul import este accidentul cel mai
frecvent în practică și singurul care se manifestă ca stoc dublat fără nicio
eroare.

**D6 — NIR cu serie și contor proprii.** `CompanySettings` primește
`receptionSeries` (implicit `NIR`) și `receptionNextNumber`. Numerotarea este
complet separată de cea a facturilor emise: sunt documente diferite, cu
destinatari diferiți, iar amestecarea lor face ambele registre ilizibile.

**D7 — NIR-ul este un instantaneu.** `Purchase` copiază la recepție denumirea
și codul fiscal ale furnizorului. Aceeași regulă ca la facturi: un furnizor
redenumit peste un an nu are voie să schimbe un document deja emis.

**D8 — Previzualizarea este obligatorie și gratuită.** `dryRun` există deja în
import. În modul intrare, previzualizarea arată câte produse se creează, câte se
completează, valoarea totală a recepției și numărul NIR care s-ar aloca — fără
să scrie nimic și fără să consume numărul.

**D9 — Produsele noi și cele existente intră în același document.** O livrare
conține în mod firesc și articole noi, și completări de stoc. Separarea lor în
două documente ar produce două NIR-uri pentru o singură recepție fizică.

**D10 — Costul se mediază ponderat, la fel pe ambele căi.**
`nou = (stoc_vechi × cost_vechi + cantitate × cost_nou) / (stoc_vechi +
cantitate)`, rotunjit la doi bani. Când stocul anterior este zero sau costul
lipsește, se ia direct costul nou: nu există nimic de mediat.

## E. Model de date

`purchases` primește:

| coloană | rol |
|---|---|
| `reception_series`, `reception_number` | numerotarea proprie a NIR-ului |
| `reception_issued_at` | data recepției |
| `supplier_name`, `supplier_tax_id` | instantaneul furnizorului |
| `source_file_name`, `source_file_hash` | fișierul care a produs intrarea |

Cheie unică pe `source_file_hash`, care face dublul import imposibil la nivel de
bază, nu doar de verificare în cod.

`company_settings` primește `reception_series` și `reception_next_number`.

## F. Inventar de fișiere

**Backend, fișiere noi (6):**

```
service/StockIntakeService.java
service/GoodsReceiptService.java
service/ReceptionNotePdfService.java
dto/GoodsReceiptRequest.java
dto/GoodsReceiptResultDto.java
controller/GoodsReceiptController.java
```

**Backend, fișiere modificate (7):**

```
model/Purchase.java              + campurile de receptie si instantaneu
model/CompanySettings.java       + seria si contorul NIR
dto/CompanySettingsDto.java      + cele doua campuri
service/CompanySettingsService.java   mapare pentru cele doua campuri
service/ProductImportService.java     parsarea expusa pentru reutilizare
service/PurchaseService.java     deleaga stocul catre StockIntakeService
repository/PurchaseRepository.java    cautare dupa amprenta si numar maxim
```

**Backend, teste noi (2):**

```
service/StockIntakeServiceTest.java
service/GoodsReceiptValidationTest.java
```

**Frontend, fișiere modificate (3):**

```
api/productService.js            apelul de import ca intrare
pages/admin/AdminProducts.jsx    bifa si campurile de furnizor si factura
pages/admin/AdminPurchases.jsx   numarul NIR si descarcarea documentului
```

Total: 18 fișiere.

## G. Criterii de acceptanță

1. Un import cu bifa de intrare adaugă fiecare cantitate în stoc exact o dată.
2. Costul produsului devine media ponderată corectă, verificată numeric.
3. O achiziție creată din pagina Cumpărări actualizează și ea costul.
4. Un fișier cu un rând fără preț de achiziție este respins integral, cu lista
   rândurilor, și nu scrie nimic.
5. Reîncărcarea aceluiași fișier este respinsă cu trimitere la recepția
   existentă.
6. `dryRun` nu creează nimic și nu consumă numărul NIR.
7. Două recepții consecutive primesc numere NIR consecutive.
8. Numerotarea NIR nu atinge contorul facturilor emise.
9. Un import fără bifă se comportă exact ca înainte și nu creează recepție.
10. NIR-ul tipărit conține denumirea furnizorului de la data recepției, chiar
    dacă furnizorul a fost redenumit ulterior.
