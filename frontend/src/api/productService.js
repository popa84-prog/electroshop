import api from './axios';

const productService = {
  list: (params = {}) => api.get('/products', { params }).then((r) => r.data.data),
  getById: (id) => api.get(`/products/${id}`).then((r) => r.data.data),
  categories: () => api.get('/products/categories').then((r) => r.data.data),
  categoryTree: () => api.get('/products/category-tree').then((r) => r.data.data),
  brands: () => api.get('/products/brands').then((r) => r.data.data),
  companyInfo: () => api.get('/products/company-info').then((r) => r.data.data),
  // Real categories ranked by how many products they hold.
  topCategories: (limit = 4) =>
    api.get('/products/top-categories', { params: { limit } }).then((r) => r.data.data),
  // Ofertele afișabile acum într-o zonă: 'HOME_PROMO' (modulul mare) sau
  // 'BENEFIT_BAR' (banda de patru cartonașe). Vezi ProductController#offers.
  offers: (placement = 'HOME_PROMO') =>
    api.get('/products/offers', { params: { placement } }).then((r) => r.data.data),

  importProducts: (file, dryRun = true, restock = false) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post(`/products/import?dryRun=${dryRun}&restock=${restock}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.data);
  },

  // Sync ONLY the purchase price (pret achizitie) on existing products, by name.
  // Does not create/delete products or change stock, price or categories.
  syncPurchasePrices: (file, dryRun = true) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post(`/products/sync-purchase-prices?dryRun=${dryRun}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.data);
  },

  // Repara categoria/subcategoria produselor deja existente in baza de date,
  // folosind acelasi tabel de reguli ca importul.
  //   mode = 'PLACEHOLDER'  -> doar valorile inutilizabile ("0", "-", "Folosit", gol)
  //   mode = 'INCONSISTENT' -> cele de mai sus + perechile care contrazic taxonomia
  //   mode = 'ALL'          -> recalculeaza tot din denumirea produsului
  // Cu dryRun = true nu se scrie nimic; raspunsul contine exact lista de
  // modificari pe care ar aplica-o rularea reala.
  recategorize: (mode = 'INCONSISTENT', dryRun = true) =>
    api
      .post(`/products/recategorize?mode=${mode}&dryRun=${dryRun}`)
      .then((r) => r.data.data),

  // Admin
  create: (payload) => api.post('/products', payload).then((r) => r.data.data),
  update: (id, payload) => api.put(`/products/${id}`, payload).then((r) => r.data.data),
  remove: (id) => api.delete(`/products/${id}`).then((r) => r.data),
  // Permanently removes a product AND its historical order/purchase line
  // items — the explicit, irreversible override offered only after a normal
  // remove()/bulkRemove() reports the product was deactivated instead of
  // deleted (it has sales/purchase history). Requires PRODUCTS_FORCE_DELETE
  // (Admin-only by default) — a Manager-role call gets a 403 like any other
  // permission-gated endpoint.
  forceRemove: (id) => api.delete(`/products/${id}/force`).then((r) => r.data),
  bulkForceRemove: (ids) => api.post('/products/bulk-force-delete', { ids }).then((r) => r.data.data),
  // Hide/show on the storefront without deleting (feature #5).
  activate: (id) => api.post(`/products/${id}/activate`).then((r) => r.data.data),
  deactivate: (id) => api.post(`/products/${id}/deactivate`).then((r) => r.data.data),
  // Feature #10 — "VÂNDUT" quick sale: records a walk-in sale, decrements stock,
  // creates a completed order (feeds the dashboard automatically) and returns the
  // fresh product row.
  sell: (id, payload) => api.post(`/products/${id}/sell`, payload).then((r) => r.data.data),
  // Batch delete. POST (not DELETE) because the call is not idempotent and
  // request bodies on DELETE are unreliable across proxies.
  bulkRemove: (ids) => api.post('/products/bulk-delete', { ids }).then((r) => r.data.data),
  // Batch activate/deactivate — the products table's "Activează selectate" /
  // "Dezactivează selectate" batch-toolbar actions. Same POST-with-body shape
  // as bulkRemove, and returns { updated, notFound }.
  bulkActivate: (ids) => api.post('/products/bulk-activate', { ids }).then((r) => r.data.data),
  bulkDeactivate: (ids) => api.post('/products/bulk-deactivate', { ids }).then((r) => r.data.data),
  uploadImage: (id, file) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post(`/products/${id}/image`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data.data);
  },

  // ---- Cloudinary image gallery (feature #5) ----
  // Uploads one file at a time (keeps each request small) and returns the
  // latest product detail (with the full image list) after the last upload.
  uploadProductImages: async (id, files) => {
    let last = null;
    for (const file of files) {
      const form = new FormData();
      form.append('files', file);
      // eslint-disable-next-line no-await-in-loop
      const r = await api.post(`/products/${id}/images`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      last = r.data.data;
    }
    return last;
  },
  deleteProductImage: (id, imageId) =>
    api.delete(`/products/${id}/images/${imageId}`).then((r) => r.data.data),
  setPrimaryImage: (id, imageId) =>
    api.put(`/products/${id}/images/${imageId}/primary`).then((r) => r.data.data),
  // Saves the new drag & drop order — imageIds must be the full, ordered id list.
  reorderProductImages: (id, imageIds) =>
    api.put(`/products/${id}/images/reorder`, { imageIds }).then((r) => r.data.data),
};

export default productService;
