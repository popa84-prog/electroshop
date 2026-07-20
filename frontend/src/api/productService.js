import api from './axios';

const productService = {
  list: (params = {}) => api.get('/products', { params }).then((r) => r.data.data),
  getById: (id) => api.get(`/products/${id}`).then((r) => r.data.data),
  categories: () => api.get('/products/categories').then((r) => r.data.data),
  categoryTree: () => api.get('/products/category-tree').then((r) => r.data.data),
  brands: () => api.get('/products/brands').then((r) => r.data.data),

  importProducts: (file, dryRun = true) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post(`/products/import?dryRun=${dryRun}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data.data);
  },

  // Admin
  create: (payload) => api.post('/products', payload).then((r) => r.data.data),
  update: (id, payload) => api.put(`/products/${id}`, payload).then((r) => r.data.data),
  remove: (id) => api.delete(`/products/${id}`).then((r) => r.data),
  uploadImage: (id, file) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post(`/products/${id}/image`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data.data);
  },
};

export default productService;
