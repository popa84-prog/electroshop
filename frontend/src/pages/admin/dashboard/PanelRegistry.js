/**
 * The catalogue of dashboard panels. Tasks 1 and 4.
 *
 * One declaration per panel: what it is called, what permission it needs, how
 * wide it is by default, and which component renders it.
 *
 * ## Why a registry rather than JSX in the page
 *
 * The grid is configurable — cards move, hide and resize — so the page cannot
 * hard-code an order. It renders whatever the saved layout says, and the layout
 * is a list of ids. This file is what turns an id back into a component, a
 * title and a permission check, and it is the only place that mapping exists.
 *
 * ## The ids must match the backend
 *
 * `AdminPreferenceService` holds the same list, because it is the server that
 * reconciles a stored layout against the current panel set: it drops ids it does
 * not recognise and appends panels the layout has never seen. Two lists that
 * must agree is a real cost, and the alternative was worse — the server would
 * have to either store an opaque blob it cannot validate, or learn the frontend's
 * component tree. A short list of strings, checked by a test, is the cheaper of
 * the two.
 *
 * ## Permissions gate rendering, not just the API
 *
 * A panel whose permission the operator lacks is never mounted, so it does not
 * fire a request that would 403 and then paint an error card. The server still
 * enforces the same rule — this is convenience, not security — but a dashboard
 * full of red permission errors is a dashboard people learn to ignore.
 */

import ActivityPanel from './ActivityPanel';
import AdminToolsPanel from './AdminToolsPanel';
import AiAssistantPanel from './AiAssistantPanel';
import BusinessBanner from './BusinessBanner';
import CustomerInsightsPanel from './CustomerInsightsPanel';
import FinancialOverviewPanel from './FinancialOverviewPanel';
import HealthStatusPanel from './HealthStatusPanel';
import InventoryHealthPanel from './InventoryHealthPanel';
import MarketingPerformancePanel from './MarketingPerformancePanel';
import OperationalLogsPanel from './OperationalLogsPanel';
import OrderEfficiencyPanel from './OrderEfficiencyPanel';
import PredictiveSalesPanel from './PredictiveSalesPanel';
import ProductPerformancePanel from './ProductPerformancePanel';
import ProfitBreakdownPanel from './ProfitBreakdownPanel';
import SalesChartPanel from './SalesChartPanel';
import SystemPanel from './SystemPanel';
import TopProductsPanel from './TopProductsPanel';

/**
 * Every panel, in default display order.
 *
 * The order here is the order a new administrator sees, and it is not
 * arbitrary: money first (banner, sales, profit, finance), then the things that
 * need acting on (inventory, products, orders), then analysis (customers,
 * marketing), then the operator's own tools and the system. Somebody opening
 * the dashboard to answer "how are we doing" gets the answer above the fold.
 */
export const PANELS = [
  {
    id: 'business-banner',
    title: 'Indicatori de business',
    permission: 'METRICS_VIEW',
    span: 12,
    component: BusinessBanner,
    // The banner is the page's headline. Hiding it is allowed — an operator who
    // lives in the order queue may not want it — but it cannot be dragged out of
    // first place, because a headline in the middle of a page is not a headline.
    pinned: true,
  },
  {
    id: 'sales-chart',
    title: 'Vânzări',
    permission: 'DASHBOARD_VIEW',
    span: 8,
    component: SalesChartPanel,
  },
  {
    id: 'predictive-sales',
    title: 'Vânzări estimate',
    permission: 'METRICS_VIEW',
    span: 4,
    component: PredictiveSalesPanel,
  },
  {
    id: 'profit-breakdown',
    title: 'Structura profitului',
    permission: 'METRICS_VIEW',
    span: 12,
    component: ProfitBreakdownPanel,
  },
  {
    id: 'financial-overview',
    title: 'Situație financiară',
    permission: 'METRICS_VIEW',
    span: 12,
    component: FinancialOverviewPanel,
  },
  {
    id: 'inventory-health',
    title: 'Sănătatea stocului',
    permission: 'METRICS_VIEW',
    span: 6,
    component: InventoryHealthPanel,
  },
  {
    id: 'top-products',
    title: 'Top produse',
    permission: 'METRICS_VIEW',
    span: 6,
    component: TopProductsPanel,
  },
  {
    id: 'product-performance',
    title: 'Performanța produselor',
    permission: 'METRICS_VIEW',
    span: 6,
    component: ProductPerformancePanel,
  },
  {
    id: 'order-efficiency',
    title: 'Eficiența comenzilor',
    permission: 'METRICS_VIEW',
    span: 6,
    component: OrderEfficiencyPanel,
  },
  {
    id: 'customer-insights',
    title: 'Analiza clienților',
    permission: 'METRICS_VIEW',
    span: 6,
    component: CustomerInsightsPanel,
  },
  {
    id: 'marketing-performance',
    title: 'Performanța campaniilor',
    permission: 'MARKETING_VIEW',
    span: 6,
    component: MarketingPerformancePanel,
  },
  {
    id: 'ai-assistant',
    title: 'Asistent administrare',
    permission: 'METRICS_VIEW',
    span: 8,
    component: AiAssistantPanel,
  },
  {
    id: 'health-status',
    title: 'Stare sistem',
    permission: 'SYSTEM_MONITOR',
    span: 4,
    component: HealthStatusPanel,
  },
  {
    id: 'activity',
    title: 'Activitate recentă',
    permission: 'AUDIT_VIEW',
    span: 6,
    component: ActivityPanel,
  },
  {
    id: 'admin-tools',
    title: 'Unelte',
    permission: 'TOOLS_USE',
    span: 6,
    component: AdminToolsPanel,
  },
  {
    id: 'operational-logs',
    title: 'Jurnal operațional',
    permission: 'SYSTEM_MONITOR',
    span: 6,
    component: OperationalLogsPanel,
  },
  {
    id: 'system',
    title: 'Sistem',
    permission: 'SYSTEM_MONITOR',
    span: 6,
    component: SystemPanel,
  },
];

/** Lookup by id, built once. */
const BY_ID = new Map(PANELS.map((panel) => [panel.id, panel]));

export function getPanel(id) {
  return BY_ID.get(id) || null;
}

/**
 * The panels an operator may see, in the order their layout specifies.
 *
 * A saved layout that references a panel the operator cannot view is not an
 * error — permissions change, and a Manager promoted to Admin should simply see
 * more. The entry is skipped and left in the stored layout untouched, so a
 * temporary demotion does not permanently erase their arrangement.
 *
 * @param {Array<{id: string, span: number, hidden: boolean}>} layout
 * @param {(permission: string) => boolean} hasPermission
 * @param {boolean} showHidden true while the layout editor is open, so hidden
 *   cards can be revealed and put back
 */
export function resolvePanels(layout, hasPermission, showHidden = false) {
  const source = layout && layout.length
    ? layout
    : PANELS.map((panel, index) => ({
        id: panel.id,
        order: index,
        span: panel.span,
        hidden: false,
      }));

  return source
    .map((state) => {
      const panel = getPanel(state.id);
      if (!panel) return null;
      if (panel.permission && !hasPermission(panel.permission)) return null;
      if (state.hidden && !showHidden) return null;
      return { ...panel, ...state };
    })
    .filter(Boolean);
}

/** Every panel the operator may see, including hidden ones, for the visibility menu. */
export function availablePanels(layout, hasPermission) {
  const hiddenById = new Map((layout || []).map((state) => [state.id, state.hidden]));
  return PANELS.filter((panel) => !panel.permission || hasPermission(panel.permission)).map(
    (panel) => ({ ...panel, hidden: Boolean(hiddenById.get(panel.id)) })
  );
}
