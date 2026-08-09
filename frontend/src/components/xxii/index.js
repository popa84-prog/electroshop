/**
 * XXII Design System — public surface.
 *
 * Layered exactly as TASK 9 specifies, so the import list itself documents the
 * architecture:
 *
 *   Atoms      GeoIcon, GlassPanel, NeonButton, NeonBadge, HoloInput, HoloLoader
 *   Molecules  StatTile, SectionHeader, HoloTimer, TiltCard, Reveal
 *   Organisms  Grid12/Module/Section, HoloCarousel, HoloGallery, HoloReviews,
 *              MiniCart, AIPicks, NeonSearch
 *
 * Consumers import from this barrel rather than from individual files, so an
 * internal file move never touches a page.
 */

/* ---- Atoms ---- */
export { default as GeoIcon, geoIconNames } from './GeoIcon';
export { default as GlassPanel } from './GlassPanel';
export { default as NeonButton } from './NeonButton';
export { default as NeonBadge } from './NeonBadge';
export { default as HoloInput } from './HoloInput';
export { default as HoloLoader, HoloSkeleton, HoloGridSkeleton } from './HoloLoader';

/* ---- Molecules ---- */
export { default as StatTile } from './StatTile';
export { default as SectionHeader } from './SectionHeader';
export { default as HoloTimer } from './HoloTimer';
export { default as TiltCard } from './TiltCard';
export { default as Reveal } from './Reveal';

/* ---- Organisms ---- */
export { default as Grid12, Module, Section } from './Grid';
export { default as HoloCarousel } from './HoloCarousel';
export { default as HoloGallery } from './HoloGallery';
export { default as HoloReviews } from './HoloReviews';
export { default as MiniCart } from './MiniCart';
export { default as AIPicks } from './AIPicks';
export { default as NeonSearch } from './NeonSearch';


/* ---- Dashboard kit (tasks 1-20) ----
 *
 * Added for the dashboard redesign. Everything here is composed from the atoms
 * above rather than introducing a second visual language: DashCard is a
 * GlassPanel with a standard header, the badges reuse the validated status
 * palette from ChartTheme, and the tooltip draws its swatches from the same
 * series order every chart uses. A card that looked like it came from a
 * different system would be the most visible possible failure of a redesign
 * whose stated goal is that the panels read as one thing.
 */
export { default as LucideIcon, lucideIconNames } from './LucideIcon';
export { default as DashCard } from './DashCard';
export { default as CountUp } from './CountUp';
export { default as TrendPill } from './TrendPill';
export { default as SeverityBadge } from './SeverityBadge';
export { default as DataTable } from './DataTable';
export { default as EmptyState } from './EmptyState';
export { default as ExportButton } from './ExportButton';
export { default as Breadcrumbs } from './Breadcrumbs';
export { default as SearchField } from './SearchField';
export { default as AdvancedTooltip } from './AdvancedTooltip';
export {
  default as RangeSwitch,
  RANGE_OPTIONS,
  DEFAULT_RANGES,
  FINANCIAL_RANGES,
  SHORT_RANGES,
} from './RangeSwitch';

/* ---- Templates ---- */
export { default as AuthShell } from './AuthShell';

/* ---- Chart theme (TASK 6) ---- */
export {
  XX_CHART_SURFACE,
  XX_SERIES,
  XX_SERIES_BLUE,
  XX_SERIES_AMBER,
  XX_SERIES_MAGENTA,
  XX_SERIES_GREEN,
  XX_SERIES_PURPLE,
  XX_SERIES_CYAN,
  XX_STATUS,
  XX_STATUS_UNKNOWN,
  XX_GRID_STROKE,
  XX_AXIS_INK,
  XX_GLOW_FILTER,
  xxAxisProps,
  xxGridProps,
  xxLegendProps,
  xxCursor,
  xxBarCursor,
  XXChartDefs,
  HoloTooltip,
} from './ChartTheme';
