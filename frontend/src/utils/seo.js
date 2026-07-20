import { useEffect } from 'react';

const SITE = 'ElectroShop';
const BASE_URL = 'https://electroshop-alpha.vercel.app';

function upsertMeta(attr, key, content) {
  if (!content) return;
  let el = document.head.querySelector(`meta[${attr}="${key}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute(attr, key);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content);
}

function upsertCanonical(url) {
  let el = document.head.querySelector('link[rel="canonical"]');
  if (!el) {
    el = document.createElement('link');
    el.setAttribute('rel', 'canonical');
    document.head.appendChild(el);
  }
  el.setAttribute('href', url);
}

/**
 * Lightweight, dependency-free SEO: updates the document title, meta
 * description, canonical URL and Open Graph / Twitter tags for the current page.
 */
export function useSeo({ title, description, path, image } = {}) {
  useEffect(() => {
    const fullTitle = title ? `${title} · ${SITE}` : `${SITE} – Magazin de electronice`;
    document.title = fullTitle;

    if (description) upsertMeta('name', 'description', description);

    const url = BASE_URL + (path || '/');
    upsertCanonical(url);

    upsertMeta('property', 'og:title', fullTitle);
    if (description) upsertMeta('property', 'og:description', description);
    upsertMeta('property', 'og:url', url);
    if (image) upsertMeta('property', 'og:image', image);

    upsertMeta('name', 'twitter:title', fullTitle);
    if (description) upsertMeta('name', 'twitter:description', description);
  }, [title, description, path, image]);
}
