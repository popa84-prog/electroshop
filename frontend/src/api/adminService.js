import api from './axios';

const adminService = {
  dashboard: () => api.get('/admin/dashboard').then((r) => r.data.data),

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
};

export default adminService;
