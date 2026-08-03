/**
 * XXII — TASK 7 (Predictive Shopping: the scoring engine behind "AI Picks").
 *
 * The backend exposes no recommendations endpoint, so ranking happens on the
 * client from signals the browser already has. Nothing here is sent anywhere —
 * the behaviour log lives in localStorage and is used only to reorder products
 * the API already returned.
 *
 * The score is a weighted sum of six independent signals. Each returns 0..1 so
 * the weights alone determine influence and a signal can be added or removed
 * without rebalancing the rest.
 *
 *   affinity  0.34 — how strongly the user has engaged with this category
 *   recency   0.16 — how recently that category was touched
 *   cart      0.14 — complements what is in the cart right now
 *   quality   0.14 — rating, damped by how many ratings back it
 *   momentum  0.12 — stock movement / featured standing
 *   value     0.10 — price relative to what the user actually looks at
 *
 * A deterministic jitter derived from the product id breaks ties without
 * `Math.random()`, so the same inputs always produce the same order — a list
 * that reshuffles on every render destroys the user's spatial memory.
 */

const STORAGE_KEY = 'es_xx_behavior_v1';
const MAX_EVENTS = 160;
const HALF_LIFE_MS = 1000 * 60 * 60 * 72; // 72h — a category cools off over three days

const WEIGHTS = {
  affinity: 0.34,
  recency: 0.16,
  cart: 0.14,
  quality: 0.14,
  momentum: 0.12,
  value: 0.1,
};

/* ------------------------------------------------------------------ */
/* Behaviour log                                                       */
/* ------------------------------------------------------------------ */

function safeRead() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    // Private mode, disabled storage, or corrupt JSON. Recommendations degrade
    // to the popularity-only path rather than breaking the page.
    return [];
  }
}

function safeWrite(events) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(events.slice(-MAX_EVENTS)));
  } catch {
    /* storage unavailable — behaviour tracking is best-effort by design */
  }
}

/**
 * Records an interaction. `type` is one of 'view' | 'cart' | 'search' | 'buy',
 * each with its own weight because adding to a cart says far more about intent
 * than opening a page does.
 */
export function trackEvent(type, payload = {}) {
  if (typeof window === 'undefined') return;
  const events = safeRead();
  events.push({
    t: type,
    c: payload.category || null,
    p: payload.productId || null,
    v: typeof payload.price === 'number' ? payload.price : null,
    q: payload.query || null,
    at: Date.now(),
  });
  safeWrite(events);
}

export function trackProductView(product) {
  if (!product) return;
  trackEvent('view', {
    category: categoryOf(product),
    productId: product.id,
    price: Number(product.price) || null,
  });
}

export function trackAddToCart(product) {
  if (!product) return;
  trackEvent('cart', {
    category: categoryOf(product),
    productId: product.id,
    price: Number(product.price) || null,
  });
}

export function trackSearch(query) {
  if (!query) return;
  trackEvent('search', { query: String(query).slice(0, 60) });
}

export function clearBehavior() {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* nothing to do */
  }
}

/* ------------------------------------------------------------------ */
/* Profile                                                             */
/* ------------------------------------------------------------------ */

const TYPE_WEIGHT = { view: 1, search: 1.2, cart: 3, buy: 5 };

/**
 * Collapses the raw event log into a profile:
 *
 *   categories   category → decayed weight, normalised so the top is 1
 *   lastSeen     category → timestamp of the most recent touch
 *   avgPrice     the mean price of everything the user actually engaged with
 *   viewed       ids already seen, so AI Picks can avoid re-recommending them
 */
export function buildProfile() {
  const events = typeof window === 'undefined' ? [] : safeRead();
  const now = Date.now();

  const categories = {};
  const lastSeen = {};
  const viewed = new Set();
  let priceSum = 0;
  let priceCount = 0;

  events.forEach((event) => {
    if (event.p) viewed.add(event.p);

    if (typeof event.v === 'number' && event.v > 0) {
      priceSum += event.v;
      priceCount += 1;
    }

    if (!event.c) return;

    // Exponential decay: an interaction one half-life old counts half as much.
    const age = Math.max(0, now - (event.at || now));
    const decay = Math.pow(0.5, age / HALF_LIFE_MS);
    const weight = (TYPE_WEIGHT[event.t] || 1) * decay;

    categories[event.c] = (categories[event.c] || 0) + weight;
    lastSeen[event.c] = Math.max(lastSeen[event.c] || 0, event.at || 0);
  });

  const max = Object.values(categories).reduce((acc, value) => Math.max(acc, value), 0);
  if (max > 0) {
    Object.keys(categories).forEach((key) => {
      categories[key] /= max;
    });
  }

  return {
    categories,
    lastSeen,
    viewed,
    avgPrice: priceCount > 0 ? priceSum / priceCount : null,
    eventCount: events.length,
    hasHistory: events.length >= 3,
  };
}

/* ------------------------------------------------------------------ */
/* Scoring                                                             */
/* ------------------------------------------------------------------ */

/**
 * The API is inconsistent about how a category arrives: the list endpoint sends
 * `category` as a plain string, some detail payloads send `categoryName`, and
 * nested objects appear in admin responses. All three are normalised here so
 * every signal downstream compares like with like.
 */
function categoryOf(product) {
  if (!product) return null;
  if (typeof product.categoryName === 'string' && product.categoryName) return product.categoryName;
  if (typeof product.category === 'string' && product.category) return product.category;
  if (product.category && typeof product.category.name === 'string') return product.category.name;
  return null;
}

/** Deterministic 0..1 noise from the product id — stable across renders. */
function jitter(id) {
  const n = Number(id) || 0;
  return ((n * 2654435761) % 1000) / 1000;
}

function affinityScore(product, profile) {
  const category = categoryOf(product);
  if (!category) return 0.15;
  return profile.categories[category] ?? 0.1;
}

function recencyScore(product, profile) {
  const category = categoryOf(product);
  const seen = category ? profile.lastSeen[category] : 0;
  if (!seen) return 0;
  const age = Date.now() - seen;
  return Math.pow(0.5, age / HALF_LIFE_MS);
}

function cartScore(product, cartItems) {
  if (!cartItems || cartItems.length === 0) return 0;

  const category = categoryOf(product);
  const inCart = cartItems.some((item) => Number(item.id ?? item.productId) === Number(product.id));
  // Already in the cart: never recommend it again.
  if (inCart) return -1;

  const sameCategory = cartItems.some((item) => {
    const itemCategory = categoryOf(item);
    return itemCategory && itemCategory === category;
  });
  return sameCategory ? 1 : 0.2;
}

function qualityScore(product) {
  const rating = Number(product.averageRating ?? product.rating ?? 0);
  const count = Number(product.reviewCount ?? product.ratingCount ?? 0);
  if (!rating) return 0.35;
  // Bayesian damping: a 5.0 from one review must not outrank a 4.6 from fifty.
  const confidence = count / (count + 8);
  return (rating / 5) * (0.45 + 0.55 * confidence);
}

function momentumScore(product) {
  const stock = Number(product.stockQuantity ?? 0);
  if (stock <= 0) return 0; // never surface something that cannot be bought
  const featured = product.featured === true ? 0.3 : 0;
  // Low-but-present stock signals a product that is actually moving.
  const scarcity = stock < 8 ? 0.35 : stock < 30 ? 0.55 : 0.4;
  return Math.min(1, scarcity + featured);
}

function valueScore(product, profile) {
  const price = Number(product.price) || 0;
  if (!price) return 0.3;
  if (!profile.avgPrice) return 0.5;
  // Peaks when the price matches the user's observed bracket and falls off
  // symmetrically in both directions.
  const ratio = price / profile.avgPrice;
  const distance = Math.abs(Math.log(ratio));
  return Math.max(0, 1 - distance / 1.6);
}

/**
 * Scores one product and returns the score together with the single strongest
 * contributing signal, so the UI can state *why* an item was recommended.
 * An opaque "AI Recommended" badge is a black box; a reason is a feature.
 */
export function scoreProduct(product, profile, cartItems) {
  const parts = {
    affinity: affinityScore(product, profile),
    recency: recencyScore(product, profile),
    cart: cartScore(product, cartItems),
    quality: qualityScore(product),
    momentum: momentumScore(product),
    value: valueScore(product, profile),
  };

  if (parts.cart < 0) return { score: -1, reason: null, parts };
  if (parts.momentum === 0) return { score: -1, reason: null, parts };

  const score =
    Object.keys(WEIGHTS).reduce((acc, key) => acc + WEIGHTS[key] * parts[key], 0) + jitter(product.id) * 0.02;

  const REASONS = {
    affinity: 'Se potrivește cu ce ai explorat',
    recency: 'Din categoria ta recentă',
    cart: 'Completează coșul tău',
    quality: 'Cel mai bine cotat',
    momentum: 'Se vinde rapid acum',
    value: 'În bugetul tău obișnuit',
  };

  const strongest = Object.keys(WEIGHTS).reduce(
    (best, key) => (WEIGHTS[key] * parts[key] > WEIGHTS[best] * parts[best] ? key : best),
    'quality'
  );

  return { score, reason: REASONS[strongest], parts };
}

/**
 * Ranks a catalogue slice. `exclude` removes the product currently on screen so
 * a detail page never recommends the item the user is already looking at.
 */
export function recommend(products, { cartItems = [], exclude = [], limit = 4, profile } = {}) {
  if (!Array.isArray(products) || products.length === 0) return [];

  const resolved = profile || buildProfile();
  const excluded = new Set(exclude.map(Number));

  return products
    .filter((product) => product && !excluded.has(Number(product.id)))
    .map((product) => {
      const { score, reason } = scoreProduct(product, resolved, cartItems);
      return { product, score, reason };
    })
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit);
}

export default recommend;
