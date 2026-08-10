import api from './axios';

const adminService = {
  dashboard: () => api.get('/admin/dashboard').then((r) => r.data.data),

  // Audit log — params: action, entityType, entityId, page, size (all optional)
  listAuditLogs: (params = {}) => api.get('/admin/audit-logs', { params }).then((r) => r.data.data),
  exportAuditLogs: (params = {}) =>
    api.get('/admin/audit-logs/export', { params, responseType: 'blob' }).then((r) => r.data),

  // Users
  listUsers: (params = {}) => api.get('/admin/users', { params }).then((r) => r.data.data),
  getUser: (id) => api.get(`/admin/users/${id}`).then((r) => r.data.data),
  createUser: (payload) => api.post('/admin/users', payload).then((r) => r.data.data),
  updateUser: (id, payload) => api.put(`/admin/users/${id}`, payload).then((r) => r.data.data),
  deleteUser: (id) => api.delete(`/admin/users/${id}`).then((r) => r.data),

  // Account approval (pending self-registrations)
  listPendingUsers: (params = {}) =>
    api.get('/admin/users/pending', { params }).then((r) => r.data.data),
  approveUser: (id) => api.post(`/admin/users/${id}/approve`).then((r) => r.data.data),

  // Feature #6 — brute-force unlock + admin override to turn off a lost-device 2FA.
  unlockUser: (id) => api.post(`/admin/users/${id}/unlock`).then((r) => r.data.data),
  disableUserTwoFactor: (id) => api.post(`/admin/users/${id}/disable-2fa`).then((r) => r.data.data),

  // Connection log (login events with IP + location)
  loginEvents: (params = {}) => api.get('/admin/login-events', { params }).then((r) => r.data.data),

  // Stock list export: produs / achiziție / preț vânzare / stoc.
  // Returns a Blob — the caller turns it into a download.
  exportProducts: (params = {}) =>
    api.get('/admin/products/export', { params, responseType: 'blob' }).then((r) => r.data),

  // Orders
  listOrders: (params = {}) => api.get('/admin/orders', { params }).then((r) => r.data.data),
  getOrder: (id) => api.get(`/admin/orders/${id}`).then((r) => r.data.data),
  updateOrderStatus: (id, status) =>
    api.put(`/admin/orders/${id}/status`, { status }).then((r) => r.data.data),
  deleteOrder: (id) => api.delete(`/admin/orders/${id}`).then((r) => r.data),
  exportOrders: (params = {}) =>
    api.get('/admin/orders/export', { params, responseType: 'blob' }).then((r) => r.data),
  // PDF invoice for one order (feature #9)
  downloadInvoice: (id) =>
    api.get(`/admin/orders/${id}/invoice`, { responseType: 'blob' }).then((r) => r.data),

  // Suppliers (furnizori)
  // ---- Operații în masă asupra comenzilor ----
  //
  // Fiecare întoarce un raport, nu doar succes sau eșec: o selecție de câteva
  // zeci de comenzi conține aproape sigur una care nu poate primi acțiunea, iar
  // aceea nu are voie să oprească restul lotului.

  /** Identificatorii tuturor comenzilor care corespund filtrului curent. */
  orderIdsMatching: (status) =>
    api
      .get('/admin/orders/ids', { params: status ? { status } : {} })
      .then((r) => r.data.data),

  /** Câte bucăți s-ar întoarce în stoc dacă lotul ar fi anulat. Nu scrie nimic. */
  previewBulkCancel: (ids) =>
    api.post('/admin/orders/bulk-cancel-preview', { ids }).then((r) => r.data.data),

  bulkOrderStatus: (ids, status) =>
    api.post('/admin/orders/bulk-status', { ids, status }).then((r) => r.data.data),

  bulkDeleteOrders: (ids) =>
    api.post('/admin/orders/bulk-delete', { ids }).then((r) => r.data.data),

  listSuppliers: (params = {}) => api.get('/admin/suppliers', { params }).then((r) => r.data.data),
  createSupplier: (payload) => api.post('/admin/suppliers', payload).then((r) => r.data.data),
  updateSupplier: (id, payload) => api.put(`/admin/suppliers/${id}`, payload).then((r) => r.data.data),
  deleteSupplier: (id) => api.delete(`/admin/suppliers/${id}`).then((r) => r.data),

  // Purchases (intrări de marfă)
  listPurchases: (params = {}) => api.get('/admin/purchases', { params }).then((r) => r.data.data),
  getPurchase: (id) => api.get(`/admin/purchases/${id}`).then((r) => r.data.data),
  createPurchase: (payload) => api.post('/admin/purchases', payload).then((r) => r.data.data),
  deletePurchase: (id) => api.delete(`/admin/purchases/${id}`).then((r) => r.data),

  // Accounting report
  accountingReport: (params = {}) =>
    api.get('/admin/accounting/report', { params }).then((r) => r.data.data),

  // Products (for purchase item selection)
  listProductsAll: (params = { page: 0, size: 200 }) =>
    api.get('/products', { params }).then((r) => r.data.data),

  // Admin product views WITH purchase price + profit (feature #2)
  listAdminProducts: (params = {}) =>
    api.get('/admin/products', { params }).then((r) => r.data.data),
  getAdminProduct: (id) => api.get(`/admin/products/${id}`).then((r) => r.data.data),

  // Company / billing settings (feature #9)
  getCompanySettings: () => api.get('/admin/company-settings').then((r) => r.data.data),
  updateCompanySettings: (payload) =>
    api.put('/admin/company-settings', payload).then((r) => r.data.data),

  // Notification center (feature #8) — params: type, unreadOnly, page, size
  listNotifications: (params = {}) => api.get('/admin/notifications', { params }).then((r) => r.data.data),
  unreadNotificationCount: () =>
    api.get('/admin/notifications/unread-count').then((r) => r.data.data),
  markNotificationRead: (id) => api.post(`/admin/notifications/${id}/read`).then((r) => r.data.data),
  markAllNotificationsRead: () => api.post('/admin/notifications/read-all').then((r) => r.data),

  // Offers (oferte comerciale — promoția de pe prima pagină + banda de beneficii)
  listOffers: (params = {}) => api.get('/admin/offers', { params }).then((r) => r.data.data),
  getOffer: (id) => api.get(`/admin/offers/${id}`).then((r) => r.data.data),
  createOffer: (payload) => api.post('/admin/offers', payload).then((r) => r.data.data),
  updateOffer: (id, payload) => api.put(`/admin/offers/${id}`, payload).then((r) => r.data.data),
  toggleOffer: (id) => api.post(`/admin/offers/${id}/toggle`).then((r) => r.data.data),
  deleteOffer: (id) => api.delete(`/admin/offers/${id}`).then((r) => r.data),
};

export default adminService;
