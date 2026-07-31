import api from './axios';

const authService = {
  register: (payload) => api.post('/auth/register', payload).then((r) => r.data),
  login: (payload) => api.post('/auth/login', payload).then((r) => r.data.data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }).then((r) => r.data.data),
  me: () => api.get('/users/me').then((r) => r.data.data),

  // Feature #6 — 2FA login challenge (step 2, after a login() that returned requiresTwoFactor).
  verifyTwoFactor: (payload) => api.post('/auth/2fa/verify', payload).then((r) => r.data.data),

  // Feature #6 — Admin self-service 2FA setup/disable.
  setupTwoFactor: () => api.post('/auth/2fa/setup').then((r) => r.data.data),
  confirmTwoFactor: (code) => api.post('/auth/2fa/confirm', { code }).then((r) => r.data),
  disableTwoFactor: (code) => api.post('/auth/2fa/disable', { code }).then((r) => r.data),

  // Feature #6 — revoke every session (bumps the server-side token version).
  logoutAll: () => api.post('/auth/logout-all').then((r) => r.data),
};

export default authService;
