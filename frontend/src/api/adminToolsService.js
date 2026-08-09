import api from './axios';

/**
 * The administrator's personal workspace: notes, reminders, tasks, shortcuts.
 *
 * Task 20. Every item belongs to the authenticated caller; no method takes an
 * owner id, so there is no request in which changing a number reaches somebody
 * else's notes.
 */
const adminToolsService = {
  /** Everything the panel shows, in one call. */
  list: (signal) => api.get('/admin/tools', { signal }).then((r) => r.data.data),

  /**
   * Creates one item.
   *
   * @param {{kind: string, title?: string, content: string, dueAt?: string,
   *          priority?: number, linkTo?: string}} payload
   */
  create: (payload) => api.post('/admin/tools', payload).then((r) => r.data.data),

  /** Updates one item. The id travels in the path, which is the value that wins. */
  update: (id, payload) => api.put(`/admin/tools/${id}`, payload).then((r) => r.data.data),

  /**
   * Ticks a task off, or puts it back.
   *
   * A dedicated call rather than a full update, so ticking a box does not
   * require sending the whole record back — a stale record sent back would
   * silently revert anything changed in between.
   */
  toggle: (id) => api.post(`/admin/tools/${id}/toggle`).then((r) => r.data.data),

  /** Removes one item. */
  remove: (id) => api.delete(`/admin/tools/${id}`).then((r) => r.data),
};

export default adminToolsService;
