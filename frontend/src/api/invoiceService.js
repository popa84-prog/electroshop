import api from './axios';

/**
 * Registrul de facturi: listare, emitere, stornare, descărcare.
 *
 * Emiterea este `POST`, descărcarea este `GET`, iar separarea nu este stilistică.
 * Până la modulul acesta, numărul de factură se aloca în interiorul rutei de
 * descărcare — deci un `GET`, verbul despre care browserul, orice proxy și orice
 * mecanism de preîncărcare presupun că nu schimbă nimic, consuma definitiv un
 * număr fiscal. Aici cele două nu se mai pot confunda.
 */
const invoiceService = {
  /**
   * Registrul filtrat, împreună cu totalurile perioadei.
   *
   * Totalurile vin din același răspuns, calculate pe server peste tot setul
   * filtrat. Adunarea paginii curente în browser ar produce cifre care se
   * schimbă la trecerea la pagina a doua, ceea ce nu sunt totaluri.
   *
   * @param {{type?: string, status?: string, from?: string, to?: string,
   *          q?: string, page?: number, size?: number}} params
   */
  list: (params = {}, signal) =>
    api
      .get('/admin/invoices', { params: clean(params), signal })
      .then((r) => r.data.data),

  /** Un document, cu toate pozițiile. */
  get: (id, signal) =>
    api.get(`/admin/invoices/${id}`, { signal }).then((r) => r.data.data),

  /** Factura și toate stornările emise pentru o comandă. */
  byOrder: (orderId, signal) =>
    api
      .get(`/admin/invoices/by-order/${orderId}`, { signal })
      .then((r) => r.data.data),

  /**
   * Emite factura pentru o comandă. Alocă un număr fiscal.
   */
  issue: (orderId, notes) =>
    api
      .post('/admin/invoices', { orderId, notes: notes || null })
      .then((r) => r.data.data),

  /**
   * Emite facturile pentru mai multe comenzi deodată.
   *
   * Comenzile care au deja factură sunt sărite, nu produc eroare. Fiecare
   * emitere reușită consumă definitiv un număr fiscal, motiv pentru care
   * interfața cere o confirmare care spune câte.
   */
  issueBulk: (orderIds) =>
    api.post('/admin/invoices/bulk', { orderIds }).then((r) => r.data.data),

  /**
   * Emite un storno.
   *
   * `lines` gol înseamnă stornare totală. Altfel se trimit perechi
   * `{ lineId, quantity }` și se stornează exact cantitățile cerute.
   *
   * `restock` implicit adevărat: cazul obișnuit este că marfa se întoarce.
   * Fals pentru marfa deteriorată sau pierdută la transport, care nu a revenit
   * niciodată fizic — a forța restituirea acolo ar umfla stocul cu bucăți
   * inexistente.
   *
   * @param {number} invoiceId
   * @param {{reason: string, restock?: boolean,
   *          lines?: Array<{lineId: number, quantity: number}>}} payload
   */
  storno: (invoiceId, payload) =>
    api
      .post(`/admin/invoices/${invoiceId}/storno`, {
        reason: payload.reason,
        restock: payload.restock !== false,
        lines: payload.lines && payload.lines.length ? payload.lines : null,
      })
      .then((r) => r.data.data),

  /**
   * Descarcă PDF-ul și îl salvează pe disc.
   *
   * Numele fișierului vine din antetul `Content-Disposition`, nu se compune în
   * browser: serverul știe dacă documentul este factură sau storno, iar o a doua
   * regulă de denumire aici s-ar putea despărți de a lui.
   */
  download: async (invoiceId) => {
    const response = await api.get(`/admin/invoices/${invoiceId}/pdf`, {
      responseType: 'blob',
    });

    const suggested = filenameFrom(response.headers?.['content-disposition']);
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.download = suggested || `Document-${invoiceId}.pdf`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    // Fără revocare, fiecare descărcare ar lăsa în urmă un obiect care ține
    // conținutul PDF-ului în memorie până la reîncărcarea paginii.
    window.URL.revokeObjectURL(url);
  },
};

/** Scoate cheile goale, ca să nu ajungă `?status=` în adresă. */
function clean(params) {
  const out = {};
  Object.keys(params).forEach((key) => {
    const value = params[key];
    if (value !== undefined && value !== null && value !== '') {
      out[key] = value;
    }
  });
  return out;
}

/** Extrage numele fișierului din `Content-Disposition`. */
function filenameFrom(header) {
  if (!header) return null;
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  return match ? decodeURIComponent(match[1].trim()) : null;
}

export default invoiceService;
