import api from './axios';

const orderService = {
  place: (payload) => api.post('/orders', payload).then((r) => r.data.data),
  myOrders: (params = {}) => api.get('/orders', { params }).then((r) => r.data.data),
  getOne: (id) => api.get(`/orders/${id}`).then((r) => r.data.data),
  // Feature #10 — multi-product "VÂNDUT" sale cart: finalizes several distinct
  // products (each its own quantity/price) as ONE order in a single call.
  // `items` is [{ productId, quantity, unitPrice }, ...].
  adminSale: (items) => api.post('/orders/admin-sale', { items }).then((r) => r.data.data),
};

export default orderService;
