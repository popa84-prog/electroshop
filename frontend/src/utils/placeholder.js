/**
 * Imaginea neutră pentru un produs fără fotografie.
 *
 * <h2>Ce înlocuiește și de ce conta</h2>
 *
 * Până acum, orice produs fără fotografie primea o adresă către
 * `placehold.co`, un serviciu extern, cu textul „No Image". Asta însemna trei
 * lucruri, dintre care doar unul se vedea:
 *
 * 1. <b>O cerere către un terț la fiecare afișare.</b> 249 de produse fără
 *    fotografie înseamnă că fiecare pagină de categorie, fiecare căutare și
 *    fiecare coș deschidea conexiuni către un server pe care nu îl controlăm.
 *    Dacă acel serviciu încetinește, limitează traficul sau dispare, vitrina
 *    afișează imagini rupte. În plus, browserul vizitatorului îi trimite
 *    adresa IP și pagina de pe care vine — date ale clientului, către cineva
 *    care nu are treabă cu magazinul.
 * 2. <b>Scria „No Image", în engleză</b>, într-un magazin în română.
 * 3. <b>Era identic pentru toate.</b> O pagină de categorie arăta ca un perete
 *    de erori, nu ca un catalog.
 *
 * <h2>Cum funcționează acum</h2>
 *
 * Imaginea se desenează în browser, ca SVG, și se dă mai departe ca adresă
 * `data:` — zero cereri de rețea, zero dependențe, funcționează și offline.
 * Fiecare categorie are culoarea și simbolul ei, așa că un raft citit dintr-o
 * privire arată a catalog, nu a defecțiune.
 *
 * <h2>De ce nu seamănă cu o fotografie</h2>
 *
 * Deliberat. Simbolul este geometric, plat, cu hașură în fundal. O imagine
 * generată care ar semăna cu produsul ar induce clientul în eroare cu privire
 * la ce cumpără, ceea ce este o problemă de protecția consumatorului, nu de
 * estetică. Aici se vede imediat că este un substitut, nu marfa.
 *
 * <h2>Ce nu face</h2>
 *
 * Nu se scrie nicăieri. Produsul rămâne, în baza de date, fără fotografie —
 * exact ce trebuie, pentru că altfel interogarea care găsește produsele de
 * completat ar raporta zero, iar uneltele din pașii anteriori nu ar mai avea
 * ce să caute. Substitutul este un mod de afișare, nu un conținut.
 */

/**
 * Culoarea și simbolul fiecărei categorii reale din catalog.
 *
 * Lista nu este inventată: sunt cele 24 de categorii care chiar au produse
 * fără fotografie, în ordinea în care apar. Nuanțele sunt alese să se separe
 * între categoriile vecine pe raft, nu să fie frumoase individual.
 */
const CATEGORII = {
  Wearables: { nuanta: 258, glif: 'ceas' },
  'Foto & Video': { nuanta: 192, glif: 'camera' },
  'Diverse electronice': { nuanta: 220, glif: 'cip' },
  Audio: { nuanta: 320, glif: 'casti' },
  'Smart Home': { nuanta: 152, glif: 'casa' },
  Accesorii: { nuanta: 36, glif: 'cablu' },
  'Auto & Moto': { nuanta: 8, glif: 'roata' },
  'Periferice PC': { nuanta: 280, glif: 'tastatura' },
  Telefoane: { nuanta: 210, glif: 'telefon' },
  'Ingrijire personala': { nuanta: 340, glif: 'picatura' },
  Stocare: { nuanta: 174, glif: 'disc' },
  Tablete: { nuanta: 232, glif: 'tableta' },
  Electrocasnice: { nuanta: 96, glif: 'cutie' },
  Gaming: { nuanta: 292, glif: 'maneta' },
  'TV & Proiectoare': { nuanta: 20, glif: 'ecran' },
  Monitoare: { nuanta: 200, glif: 'ecran' },
  'Scule & Unelte': { nuanta: 46, glif: 'cheie' },
  Laptopuri: { nuanta: 244, glif: 'laptop' },
  'Sisteme PC': { nuanta: 266, glif: 'turn' },
  Retea: { nuanta: 166, glif: 'retea' },
  Traducatoare: { nuanta: 300, glif: 'bule' },
  'Instrumente muzicale': { nuanta: 330, glif: 'nota' },
  'Componente PC': { nuanta: 252, glif: 'cip' },
  Sanatate: { nuanta: 0, glif: 'inima' },
};

/**
 * Simbolurile, în sistemul de coordonate 0–100, centrate.
 *
 * Fiecare este abstract și din cel mult trei forme: la 120 de pixeli pe un
 * card, un desen mai bogat devine oricum o pată.
 *
 * <p>Culoarea este scrisă ca `%C%` și se substituie la generare. Tokenul arată
 * așa dinadins: o literă simplă precum `L` ar fi coincis cu comanda `L`
 * (lineto) din datele traseelor, iar substituția ar fi rupt desenele.</p>
 */
const GLIFURI = {
  ceas: `<rect x="34" y="14" width="32" height="12" rx="4" fill="%C%" opacity=".5"/>
    <rect x="34" y="74" width="32" height="12" rx="4" fill="%C%" opacity=".5"/>
    <rect x="28" y="26" width="44" height="48" rx="10" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M50 42v10h8" fill="none" stroke="%C%" stroke-width="4" stroke-linecap="round"/>`,
  camera: `<rect x="16" y="30" width="68" height="46" rx="8" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M38 30l5-8h14l5 8" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>
    <circle cx="50" cy="53" r="14" fill="none" stroke="%C%" stroke-width="4"/>
    <circle cx="50" cy="53" r="5" fill="%C%" opacity=".55"/>`,
  cip: `<rect x="28" y="28" width="44" height="44" rx="6" fill="none" stroke="%C%" stroke-width="4"/>
    <rect x="42" y="42" width="16" height="16" rx="2" fill="%C%" opacity=".5"/>
    <path d="M38 28v-10M50 28v-10M62 28v-10M38 72v10M50 72v10M62 72v10M28 38h-10M28 50h-10M28 62h-10M72 38h10M72 50h10M72 62h10"
      stroke="%C%" stroke-width="4" stroke-linecap="round"/>`,
  casti: `<path d="M22 60V50a28 28 0 0156 0v10" fill="none" stroke="%C%" stroke-width="4" stroke-linecap="round"/>
    <rect x="14" y="56" width="16" height="26" rx="7" fill="%C%" opacity=".55"/>
    <rect x="70" y="56" width="16" height="26" rx="7" fill="%C%" opacity=".55"/>`,
  casa: `<path d="M20 50L50 24l30 26" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>
    <path d="M28 48v30h44V48" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>
    <path d="M42 78V60h16v18" fill="%C%" opacity=".45"/>`,
  cablu: `<path d="M26 74c0-16 48-16 48-32a12 12 0 00-24 0" fill="none" stroke="%C%" stroke-width="4" stroke-linecap="round"/>
    <rect x="18" y="70" width="18" height="14" rx="4" fill="%C%" opacity=".55"/>
    <path d="M44 34v-12M56 34v-12" stroke="%C%" stroke-width="4" stroke-linecap="round"/>`,
  roata: `<circle cx="50" cy="52" r="30" fill="none" stroke="%C%" stroke-width="4"/>
    <circle cx="50" cy="52" r="10" fill="%C%" opacity=".5"/>
    <path d="M50 22v12M50 70v12M20 52h12M68 52h12" stroke="%C%" stroke-width="4" stroke-linecap="round"/>`,
  tastatura: `<rect x="14" y="34" width="72" height="38" rx="6" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M26 46h6M40 46h6M54 46h6M68 46h6M26 58h6M40 58h20M68 58h6"
      stroke="%C%" stroke-width="4" stroke-linecap="round" opacity=".7"/>`,
  telefon: `<rect x="32" y="14" width="36" height="72" rx="8" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M44 22h12" stroke="%C%" stroke-width="4" stroke-linecap="round"/>
    <rect x="40" y="32" width="20" height="36" rx="3" fill="%C%" opacity=".35"/>`,
  picatura: `<path d="M50 18c14 18 20 27 20 36a20 20 0 01-40 0c0-9 6-18 20-36z" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M42 56a8 8 0 008 8" fill="none" stroke="%C%" stroke-width="4" stroke-linecap="round" opacity=".7"/>`,
  disc: `<ellipse cx="50" cy="32" rx="28" ry="10" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M22 32v36c0 6 13 10 28 10s28-4 28-10V32" fill="none" stroke="%C%" stroke-width="4"/>
    <ellipse cx="50" cy="32" rx="8" ry="3" fill="%C%" opacity=".5"/>`,
  tableta: `<rect x="18" y="22" width="64" height="56" rx="8" fill="none" stroke="%C%" stroke-width="4"/>
    <rect x="26" y="30" width="48" height="36" rx="3" fill="%C%" opacity=".3"/>
    <circle cx="50" cy="72" r="3" fill="%C%" opacity=".7"/>`,
  cutie: `<rect x="22" y="18" width="56" height="64" rx="8" fill="none" stroke="%C%" stroke-width="4"/>
    <circle cx="50" cy="56" r="14" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M32 32h36" stroke="%C%" stroke-width="4" stroke-linecap="round" opacity=".7"/>`,
  maneta: `<rect x="14" y="38" width="72" height="34" rx="16" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M30 48v10M25 53h10" stroke="%C%" stroke-width="4" stroke-linecap="round"/>
    <circle cx="66" cy="50" r="4" fill="%C%"/><circle cx="74" cy="58" r="4" fill="%C%"/>`,
  ecran: `<rect x="14" y="24" width="72" height="44" rx="6" fill="none" stroke="%C%" stroke-width="4"/>
    <rect x="22" y="32" width="56" height="28" rx="2" fill="%C%" opacity=".3"/>
    <path d="M38 80h24M50 68v12" stroke="%C%" stroke-width="4" stroke-linecap="round"/>`,
  cheie: `<path d="M64 22a16 16 0 00-14 24L24 72l6 6 26-26a16 16 0 0018-24l-10 10-8-8 8-8z"
      fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>`,
  laptop: `<path d="M26 28h48v36H26z" fill="none" stroke="%C%" stroke-width="4"/>
    <rect x="32" y="34" width="36" height="24" rx="2" fill="%C%" opacity=".3"/>
    <path d="M14 70h72l-6 8H20z" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>`,
  turn: `<rect x="32" y="16" width="36" height="68" rx="6" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M40 28h20M40 38h20" stroke="%C%" stroke-width="4" stroke-linecap="round" opacity=".7"/>
    <circle cx="50" cy="62" r="8" fill="%C%" opacity=".45"/>`,
  retea: `<rect x="18" y="56" width="64" height="22" rx="6" fill="none" stroke="%C%" stroke-width="4"/>
    <path d="M34 56V38M50 56V30M66 56V38" stroke="%C%" stroke-width="4" stroke-linecap="round"/>
    <circle cx="34" cy="34" r="4" fill="%C%"/><circle cx="50" cy="26" r="4" fill="%C%"/><circle cx="66" cy="34" r="4" fill="%C%"/>`,
  bule: `<path d="M16 30h44v26H34L22 66V56h-6z" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>
    <path d="M68 42h16v22h-6v10l-10-10h-4" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round" opacity=".7"/>`,
  nota: `<path d="M42 70V24l30-6v46" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>
    <ellipse cx="34" cy="70" rx="10" ry="8" fill="%C%" opacity=".6"/>
    <ellipse cx="64" cy="64" rx="10" ry="8" fill="%C%" opacity=".6"/>`,
  inima: `<path d="M50 78S20 60 20 40a15 15 0 0130-8 15 15 0 0130 8c0 20-30 38-30 38z"
      fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>
    <path d="M32 50h10l5-8 6 16 5-8h10" fill="none" stroke="%C%" stroke-width="4" stroke-linecap="round"/>`,
  // Pentru o categorie pe care nu o cunoaștem: o cutie, adică „un produs".
  implicit: `<path d="M50 18l28 14v34L50 82 22 66V32z" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round"/>
    <path d="M22 32l28 14 28-14M50 46v36" fill="none" stroke="%C%" stroke-width="4" stroke-linejoin="round" opacity=".7"/>`,
};

/**
 * Memorie, ca să nu se reconstruiască același SVG la fiecare randare.
 *
 * <p>Cheia include tema, nu doar categoria. Așa, o schimbare de temă produce
 * intrări noi în loc să ceară cuiva să golească memoria la momentul potrivit —
 * iar „momentul potrivit" este exact felul de detaliu care se uită atunci când
 * se adaugă a treia temă.</p>
 */
const memorie = new Map();

/**
 * Nuanță stabilă pentru o categorie necunoscută.
 *
 * Aceeași categorie primește întotdeauna aceeași culoare, chiar dacă nu apare
 * în tabelul de mai sus — altfel o categorie adăugată mâine ar clipi în altă
 * culoare la fiecare reîncărcare.
 */
function nuantaDin(text) {
  let h = 0;
  for (let i = 0; i < text.length; i++) {
    h = (h * 31 + text.charCodeAt(i)) % 360;
  }
  return h;
}

/** Tema curentă, citită din atributul pe care îl scrie ThemeContext. */
function temaCurenta() {
  if (typeof document === 'undefined') {
    return 'dark';
  }
  return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
}

function scurteaza(text, maxim) {
  if (!text) return '';
  const t = String(text).trim();
  return t.length <= maxim ? t : `${t.slice(0, maxim - 1)}…`;
}

/** Textul devine parte din XML: caracterele lui speciale trebuie neutralizate. */
function xml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Adresa `data:` a imaginii neutre pentru o categorie.
 *
 * @param {string} categorie numele categoriei; gol dă simbolul implicit
 * @returns {string} un `data:image/svg+xml,…` gata de pus în `src`
 */
export function imagineNeutra(categorie) {
  const cat = (categorie || '').trim();
  const tema = temaCurenta();
  const cheie = `${tema}|${cat}`;
  const cachuit = memorie.get(cheie);
  if (cachuit) {
    return cachuit;
  }

  const config = CATEGORII[cat] || { nuanta: nuantaDin(cat || 'produs'), glif: 'implicit' };
  const glif = GLIFURI[config.glif] || GLIFURI.implicit;
  const h = config.nuanta;

  // Două palete, nu una întoarsă: pe hârtie albă un fundal închis ar fi o pată,
  // iar pe fundal închis un desen negru dispare. Se aleg separat.
  const p = tema === 'light'
    ? {
        fundal1: `hsl(${h} 42% 96%)`,
        fundal2: `hsl(${h} 38% 90%)`,
        linie: `hsl(${h} 46% 36%)`,
        hasura: `hsl(${h} 40% 62%)`,
        text: `hsl(${h} 30% 34%)`,
      }
    : {
        fundal1: `hsl(${h} 32% 14%)`,
        fundal2: `hsl(${h} 38% 9%)`,
        linie: `hsl(${h} 70% 72%)`,
        hasura: `hsl(${h} 60% 60%)`,
        text: `hsl(${h} 30% 72%)`,
      };

  const textEtichetei = scurteaza(cat || 'Produs', 26).toUpperCase();
  const eticheta = xml(textEtichetei);

  // Corpul literei se calculează din lungimea etichetei, nu este fix.
  //
  // Cu o mărime fixă, „DIVERSE ELECTRONICE" și „INGRIJIRE PERSONALA" ieșeau
  // din cadru și se tăiau la ambele capete — se pierdea prima și ultima
  // literă, adică exact ce făcea cuvântul de neînțeles.
  //
  // Lățimea sigură este 72, nu 100, din motivul explicat la caseta de mai jos.
  //
  // Factorul 0,95 este măsurat, nu estimat. Randate în browser, cu aceeași
  // familie de litere și aceeași spațiere, etichetele reale ale catalogului au
  // dat între 0,798 („PERIFERICE PC") și 0,930 („GAMING") — lățime împărțită
  // la numărul de caractere ori corpul literei. Două estimări din ochi, 0,72 și
  // apoi 0,86, au fost amândouă prea mici, iar rezultatul s-a văzut: eticheta
  // ieșea din cadru și pierdea prima și ultima literă. 0,95 lasă o margine
  // peste cea mai lată etichetă reală.
  const litere = Math.max(textEtichetei.length, 1);
  const corp = Math.min(6.5, Math.max(3.2, 72 / (litere * 0.95)));
  const spatiere = corp * 0.16;

  // Caseta este 100×75, adică exact 4:3.
  //
  // <h3>De ce tocmai 4:3</h3>
  //
  // Imaginile de produs se afișează cu `object-cover`, adică umplu cardul și
  // taie ce depășește. Cardul principal din vitrină este `aspect-[4/3]`, iar
  // miniaturile din listă, din coș și din căutare sunt pătrate. Alegând 4:3,
  // cardul mare — cel care se vede cel mai des și cel mai mare — nu pierde
  // nimic.
  //
  // <h3>Zona sigură</h3>
  //
  // Pe o miniatură pătrată, aceeași imagine 4:3 se taie pe laturi: rămâne
  // vizibil aproximativ x între 13 și 87. De aceea simbolul se micșorează la
  // banda 28–72, iar eticheta se calculează pentru 72 de unități, nu 100.
  // Nimic important nu stă în afara acelei benzi, la nicio proporție dintre
  // pătrat și 4:3.
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 75" preserveAspectRatio="xMidYMid slice">` +
    `<defs>` +
    `<linearGradient id="f" x1="0" y1="0" x2="0" y2="1">` +
    `<stop offset="0" stop-color="${p.fundal1}"/><stop offset="1" stop-color="${p.fundal2}"/>` +
    `</linearGradient>` +
    `<pattern id="h" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">` +
    `<line x1="0" y1="0" x2="0" y2="8" stroke="${p.hasura}" stroke-width="1" opacity=".13"/>` +
    `</pattern>` +
    `</defs>` +
    // Fundalul depășește intenționat caseta: `slice` decupează, iar un
    // dreptunghi exact de 100×118 ar fi lăsat dungi transparente la orice
    // proporție de card diferită de aceea.
    `<rect x="-60" y="-60" width="220" height="240" fill="url(#f)"/>` +
    `<rect x="-60" y="-60" width="220" height="240" fill="url(#h)"/>` +
    // Simbolul este desenat în sistemul 0–100 și adus în banda sigură:
    // micșorat la 0,62 în jurul centrului și ridicat, ca să lase loc
    // etichetei dedesubt.
    `<g transform="translate(50,30) scale(0.62) translate(-50,-50)">` +
    `${glif.replaceAll('%C%', p.linie)}</g>` +
    // Jumătate de spațiere adăugată la centru: `letter-spacing` pune spațiu și
    // după ultima literă, iar cu `text-anchor="middle"` acela ar fi împins
    // vizual textul spre stânga.
    `<text x="${(50 + spatiere / 2).toFixed(2)}" y="66" text-anchor="middle" fill="${p.text}" ` +
    `font-family="system-ui,-apple-system,Segoe UI,Roboto,sans-serif" ` +
    `font-size="${corp.toFixed(2)}" letter-spacing="${spatiere.toFixed(2)}" ` +
    `font-weight="600">${eticheta}</text>` +
    `</svg>`;

  const adresa = `data:image/svg+xml,${encodeURIComponent(svg)}`;
  memorie.set(cheie, adresa);
  return adresa;
}
