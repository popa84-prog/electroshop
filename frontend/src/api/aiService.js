import api from './axios';

/**
 * The administrator assistant: automated suggestions and product descriptions.
 *
 * Task 7.
 *
 * Both responses carry a `source` field. It is not decoration: a suggestion
 * derived from the store's own figures and a sentence written by a language
 * model have different failure modes, and the panel prints which one it is
 * showing so an operator knows how much to trust it.
 */
const aiService = {
  /**
   * Suggestions and order-pattern analysis for a window.
   *
   * Every suggestion arrives with the numbers that produced it, so it can be
   * argued with rather than merely accepted.
   */
  insights: (range = '30d', signal) =>
    api.get('/admin/ai/insights', { params: { range }, signal }).then((r) => r.data.data),

  /**
   * Composes a description for a product.
   *
   * Pass `productId` for an existing product — the attributes are then read
   * from the database rather than from the request, so the text cannot describe
   * a product the catalogue does not contain. For a product being created,
   * pass the attributes directly.
   *
   * The result is never saved anywhere. It comes back for a person to read,
   * edit and save deliberately.
   *
   * @param {{productId?: number, name?: string, brand?: string,
   *          category?: string, subcategory?: string, price?: number,
   *          sku?: string, existing?: string}} payload
   */
  describe: (payload) =>
    api.post('/admin/ai/describe', payload).then((r) => r.data.data),
};

export default aiService;
