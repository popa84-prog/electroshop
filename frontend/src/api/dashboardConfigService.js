import api from './axios';

/**
 * Dashboard layout, sidebar favourites, global search and the activity feed.
 *
 * Tasks 3, 4 and 5.
 *
 * No method takes an administrator id. The server reads it from the
 * authenticated session, which is what makes it impossible for a client to
 * reach somebody else's arrangement by changing a number.
 */
const dashboardConfigService = {
  // ---- TASK 4: layout -------------------------------------------------

  /** The saved arrangement, or the server's default when there is none. */
  getLayout: (signal) =>
    api.get('/admin/dashboard/layout', { signal }).then((r) => r.data.data),

  /**
   * Replaces the whole arrangement.
   *
   * @param {Array<{id: string, order: number, span: number, hidden: boolean}>} panels
   * @param {string} density `COMPACT` or `COMFORTABLE`
   */
  saveLayout: (panels, density) =>
    api.put('/admin/dashboard/layout', { panels, density }).then((r) => r.data.data),

  /**
   * The "Reset layout" button.
   *
   * Returns the default arrangement so the interface re-renders from the
   * server's answer rather than from a duplicate of the panel registry that
   * would have to be kept in step.
   */
  resetLayout: () =>
    api.delete('/admin/dashboard/layout').then((r) => r.data.data),

  // ---- TASK 3: favourites and search ----------------------------------

  getFavorites: (signal) =>
    api.get('/admin/favorites', { signal }).then((r) => r.data.data),

  /** @param {Array<{route: string, label: string, icon: string}>} items */
  saveFavorites: (items) =>
    api.put('/admin/favorites', { items }).then((r) => r.data.data),

  /**
   * Global search across products, orders and users.
   *
   * A group the caller cannot view comes back empty and is simply not
   * rendered — the server withholds it rather than reporting a count the
   * permission is meant to hide.
   */
  search: (q, signal) =>
    api.get('/admin/search', { params: { q }, signal }).then((r) => r.data.data),

  // ---- TASK 5: activity ------------------------------------------------

  /**
   * @param {{range?: string, category?: string, actor?: string, q?: string,
   *          page?: number, size?: number}} params
   */
  activity: (params = {}, signal) =>
    api.get('/admin/activity', { params, signal }).then((r) => r.data.data),

  /** The same rows as a CSV file. Requires the audit-export permission. */
  exportActivity: (params = {}) =>
    api.get('/admin/activity/export', { params, responseType: 'blob' }).then((r) => r.data),
};

export default dashboardConfigService;
