import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

/**
 * Tema vizuală: întunecat sau deschis.
 *
 * <h2>Unde se aplică</h2>
 *
 * Pe {@code <html>}, nu pe un container din React. Fundalul paginii, bara de
 * derulare, culoarea barei de adresă pe mobil și meniurile native de
 * {@code <select>} sunt desenate de browser în afara arborelui React — dacă
 * tema ar trăi pe un div, toate acestea ar rămâne pe vechea culoare și s-ar
 * vedea o ramă întunecată în jurul unei pagini deschise.
 *
 * Se scriu două lucruri: atributul {@code data-theme}, pe care îl citesc
 * variabilele din foaia de stil, și clasa {@code dark}, pe care o cere
 * configurația Tailwind ({@code darkMode: 'class'}). Astăzi proiectul nu
 * folosește niciun modificator {@code dark:} — le-am numărat, sunt zero — dar
 * clasa se ține sincronizată oricum: în ziua în care cineva scrie primul
 * {@code dark:text-white}, trebuie să funcționeze, nu să descopere că sistemul
 * de teme îl ignoră.
 *
 * <h2>Ce se alege la prima vizită</h2>
 *
 * Preferința sistemului de operare, prin {@code prefers-color-scheme}. Un om
 * care și-a pus telefonul pe temă întunecată a spus deja ce vrea; a-l întâmpina
 * cu alb ar fi să nu ascultăm un răspuns pe care îl avem. După prima comutare
 * manuală decide alegerea salvată, iar preferința sistemului nu o mai
 * suprascrie — inclusiv dacă sistemul comută singur la apus.
 *
 * <h2>De ce citirea din localStorage nu este într-un efect</h2>
 *
 * Starea se inițializează dintr-o funcție, deci prima randare a aplicației are
 * deja tema corectă și nu există un cadru în care React desenează una greșită.
 *
 * Asta nu este însă tot: React montează după ce browserul a pictat deja
 * documentul, așa că fundalul paginii ar pâlpâi oricum. Partea aceea o rezolvă
 * scriptul sincron din {@code index.html}, care scrie tema pe {@code <html>}
 * înainte de primul pixel. Cele două citesc aceeași cheie și aplică aceleași
 * atribute, deliberat: scriptul acoperă intervalul de dinainte de React, iar
 * contextul preia de acolo și rămâne singura sursă pentru comutările
 * ulterioare.
 *
 * Fiecare acces la {@code localStorage} este în {@code try}. Într-o fereastră
 * privată, cu cookie-urile blocate sau într-un iframe restricționat, simpla
 * citire aruncă excepție, iar o excepție aici ar opri randarea întregii
 * aplicații — pentru o preferință de culoare.
 */

const STORAGE_KEY = 'es_theme';

/** Singurele valori acceptate. Orice altceva din storage este ignorat. */
const THEMES = ['dark', 'light'];

const ThemeContext = createContext(null);

/** Citește alegerea salvată, sau null dacă nu există ori nu se poate citi. */
function readStoredTheme() {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    return THEMES.includes(saved) ? saved : null;
  } catch {
    return null;
  }
}

/** Ce preferă sistemul de operare. Implicit întunecat, ca până acum. */
function systemTheme() {
  try {
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
}

/**
 * Scrie tema pe document.
 *
 * Culoarea barei de adresă de pe mobil este luată din {@code theme-color}; fără
 * actualizarea ei, un telefon pe tema deschisă păstrează bara aproape neagră
 * din temă întunecată deasupra unei pagini albe.
 */
function applyTheme(theme) {
  const root = document.documentElement;
  root.setAttribute('data-theme', theme);
  root.classList.toggle('dark', theme === 'dark');

  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    meta.setAttribute('content', theme === 'dark' ? '#04050c' : '#eef1f9');
  }
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => readStoredTheme() || systemTheme());

  useEffect(() => {
    applyTheme(theme);
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Preferința nu se poate păstra între sesiuni, dar tema curentă
      // funcționează. Nu merită nicio notificare către utilizator.
    }
  }, [theme]);

  // Sistemul poate comuta singur (apus, programare). Urmărim schimbarea doar
  // cât timp utilizatorul nu a ales explicit — după o alegere manuală, ea
  // rămâne, altfel site-ul i-ar anula decizia la fiecare asfințit.
  useEffect(() => {
    if (readStoredTheme()) return undefined;
    let media;
    try {
      media = window.matchMedia('(prefers-color-scheme: light)');
    } catch {
      return undefined;
    }
    const onChange = (event) => setTheme(event.matches ? 'light' : 'dark');
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);

  const toggle = useCallback(() => {
    setTheme((current) => (current === 'dark' ? 'light' : 'dark'));
  }, []);

  const value = useMemo(
    () => ({ theme, isDark: theme === 'dark', toggle, setTheme }),
    [theme, toggle]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/**
 * Tema curentă și comutatorul ei.
 *
 * Aruncă dacă este folosit în afara provider-ului. Alternativa — întoarcerea
 * unei valori implicite — ar face ca un buton montat greșit să pară că
 * funcționează și să nu comute nimic, ceea ce se depanează mult mai greu decât
 * o eroare la montare.
 */
export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme trebuie folosit în interiorul <ThemeProvider>');
  }
  return ctx;
}

export default ThemeContext;
