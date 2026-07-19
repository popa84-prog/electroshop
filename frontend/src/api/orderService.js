import api from './axios';

const orderService = {
  place: (payload) => api.post('/orders', payload).then((r) => r.data.data),
  myOrders: (params = {}) => api.get('/orders', { params }).then((r) => r.data.data),
  getOne: (id) => api.get(`/orders/${id}`).then((r) => r.data.data),
};

export default orderService;
