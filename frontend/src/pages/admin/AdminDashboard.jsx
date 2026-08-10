import { useState } from 'react';
import DashboardGrid from './dashboard/DashboardGrid';
import DashboardHeader from './dashboard/DashboardHeader';
import LayoutToolbar from './dashboard/LayoutToolbar';
import useDashboardLayout from '../../hooks/useDashboardLayout';
import { useAuth } from '../../context/AuthContext';
import { HoloLoader } from '../../components/xxii';

/**
 * The administration dashboard. Tasks 1, 4 and 9.
 *
 * ## The page is a shell
 *
 * It owns three things: the header, the configuration toolbar, and the grid.
 * Every panel fetches its own data. That is a deliberate reversal of the
 * previous design, where the page loaded one large payload and handed pieces to
 * its children — which meant the slowest query on the page decided when anything
 * appeared, and one failing section blanked the whole screen.
 *
 * Panels now arrive independently: the banner is usually first because its
 * queries are simple aggregates, and the financial panel takes longer without
 * holding anything else up. A panel that fails shows an error inside its own
 * card and the rest of the dashboard carries on.
 *
 * ## The layout is loaded before anything is rendered
 *
 * The grid needs to know the order and which cards are hidden. Rendering the
 * default arrangement first and then rearranging when the saved one arrives
 * would make every visit start with a visible reshuffle.
 */
export default function AdminDashboard() {
  const [editing, setEditing] = useState(false);
  const { hasPermission } = useAuth();

  const {
    panels,
    density,
    loading,
    saveState,
    reorder,
    toggleHidden,
    toggleDensity,
    reset,
  } = useDashboardLayout();

  const compact = density === 'COMPACT';

  // `hasPermission` comes from the auth context and may be undefined for a
  // moment during a token refresh. Treating that as "no permissions" would blank
  // the dashboard mid-session, so an absent checker allows everything and the
  // server remains the authority.
  const can = (permission) => (hasPermission ? hasPermission(permission) : true);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <HoloLoader />
      </div>
    );
  }

  return (
    <div>
      <DashboardHeader
        hasPermission={can}
        compact={compact}
        actions={
          <LayoutToolbar
            editing={editing}
            onEditingChange={setEditing}
            compact={compact}
            onToggleDensity={toggleDensity}
            layout={panels}
            hasPermission={can}
            onToggleHidden={toggleHidden}
            onReset={reset}
            saveState={saveState}
          />
        }
      />

      {editing ? (
        <p className="mb-3 rounded-lg border border-[rgba(34,232,245,0.35)]
          bg-[rgba(34,232,245,0.07)] px-3 py-2 text-xs text-[color:var(--xx-ink-dim)]">
          Mod rearanjare activ. Trage cardurile de mâner sau, de la tastatură, apasă Enter pe
          mâner și folosește săgețile. Cardurile ascunse apar estompate și pot fi readuse din
          meniul „Carduri”.
        </p>
      ) : null}

      <DashboardGrid
        layout={panels}
        hasPermission={can}
        editing={editing}
        compact={compact}
        onReorder={reorder}
        onToggleHidden={toggleHidden}
      />
    </div>
  );
}
