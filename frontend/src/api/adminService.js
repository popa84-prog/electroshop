import api from './axios';

const adminService = {
  dashboard: () => api.get('/admin/dashboard').then((r) => r.data.data),

  // Audit log
  listAuditLogs: (params = {}) => api.get('/admin/audit-logs', { params }).then((r) => r.data.data),

  // Users
  listUsers: (params = {}) => api.get('/admin/users', { params }).then((r) => r.data.data),
  getUser: (id) => api.get(`/admin/users/${id}`).then((r) => r.data.data),
  createUser: (payload) => api.post('/admin/users', payload).then((r) => r.data.data),
  updateUser: (id, payload) => api.put(`/admin/users/${id}`, payload).then((r) => r.data.data),
  deleteUser: (id) => api.delete(`/admin/users/${id}`).then((r) => r.data),

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
};

export default adminService;
