import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import GeoIcon from './xxii/GeoIcon';
import NeonButton from './xxii/NeonButton';
import NeonSearch from './xxii/NeonSearch';

/**
 * XXII — TASK 4 (floating navbar: blur + glow, animated expanding menu) with
 * the mobile bottom bar the brief calls for.
 *
 * The header is not glued to the top of the viewport — it floats a few pixels
 * below it inside a rounded glass capsule, which is what makes it read as a
 * HUD panel rather than as a page chrome strip.
 *
 * It reacts to scroll: at the very top the capsule is nearly transparent and
 * borderless; past 12px it condenses — stronger fill, visible border, and a
 * blue glow underneath. That gives the page depth without a hard edge.
 *
 * Three navigation surfaces exist and they are deliberately different:
 *   - the desktop rail (links + search + account actions),
 *   - the mobile sheet (the ☰ menu, for account and secondary destinations),
 *   - the mobile bottom bar (thumb-reachable primary destinations).
 * Duplicating every link into the bottom bar would make it a scrolling list,
 * which defeats the purpose; it carries exactly four fixed targets.
 *
 * Acțiunile de cont — Login, Înregistrare, Ieșire — foloseau clasele `btn-*`
 * scrise direct pe un `<button>` sau pe un `<Link>`. Sunt acum `NeonButton`,
 * ultimul loc din magazin unde geometria butoanelor era scrisă de mână. Câștigul
 * nu este vizual, ci de comportament: unda la click, starea `charging` și
 * inelul de focus vin din același component ca peste tot, deci nu pot rămâne în
 * urmă când sistemul evoluează.
 *
 * Celelalte controale ale barei — coșul, comutatorul de meniu, butonul de
 * închidere al foii — rămân butoane proprii: sunt pătrate de 40px cu o singură
 * pictogramă, iar `NeonButton` este construit pentru butoane cu text.
 */

const CONDENSE_AT = 12;

export default function Navbar() {
  const { isAuthenticated, isAdmin, user, logout } = useAuth();
  const { totalItems } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [condensed, setCondensed] = useState(false);

  // Any route change closes the sheet — otherwise tapping a link leaves the
  // overlay covering the page the user just navigated to.
  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const onScroll = () => setCondensed(window.scrollY > CONDENSE_AT);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // The sheet is a full-screen overlay; the page behind it must not scroll.
  useEffect(() => {
    if (!open) return undefined;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  const handleLogout = () => {
    setOpen(false);
    logout();
    navigate('/login');
  };

  const navLinkClass = ({ isActive }) =>
    `relative rounded-full px-4 py-2 text-sm font-medium transition-all duration-xx ease-xx ${
      isActive
        ? 'text-white bg-[rgba(46,123,255,0.18)] shadow-[0_0_24px_-6px_rgba(46,123,255,0.8)]'
        : 'text-[#b9c1e6] hover:text-white hover:bg-white/[0.07]'
    }`;

  const sheetLinkClass = ({ isActive }) =>
    `flex items-center gap-3 rounded-xl px-4 py-3 text-base font-medium transition-all duration-xx ease-xx ${
      isActive ? 'bg-[rgba(46,123,255,0.18)] text-white' : 'text-[#b9c1e6] hover:bg-white/[0.07] hover:text-white'
    }`;

  const bottomLinkClass = ({ isActive }) =>
    `flex flex-1 flex-col items-center gap-1 py-2 text-[0.62rem] font-semibold uppercase tracking-wide transition-colors duration-xx ${
      isActive ? 'text-[color:var(--xx-cyan)]' : 'text-[#8d95c0]'
    }`;

  return (
    <>
      <header className="sticky top-0 z-40 px-3 pt-3 sm:px-4">
        <nav
          className={`mx-auto flex max-w-[1680px] items-center gap-3 rounded-2xl px-3 py-2.5 transition-all duration-xxslow ease-xx sm:gap-4 sm:px-5 ${
            condensed
              ? 'border border-[rgba(255,255,255,0.14)] bg-[rgba(7,8,24,0.78)] shadow-[0_18px_46px_-24px_rgba(0,0,0,0.95),0_0_46px_-18px_rgba(46,123,255,0.55)] backdrop-blur-glass-xl'
              : 'border border-transparent bg-[rgba(7,8,24,0.35)] backdrop-blur-glass'
          }`}
        >
          <Link
            to="/"
            className="group flex shrink-0 items-center gap-2.5"
            aria-label="ElectroShop — pagina principală"
          >
            <span
              aria-hidden="true"
              className="grid h-9 w-9 place-items-center rounded-xl bg-xx-primary shadow-glow-blue transition-transform duration-xx ease-xx group-hover:scale-110"
            >
              <GeoIcon name="bolt" className="h-5 w-5" accent="#ffffff" />
            </span>
            <span className="font-display text-lg font-bold tracking-tight xx-text-gradient sm:text-xl">
              ElectroShop
            </span>
          </Link>

          {/* Desktop rail */}
          <div className="hidden items-center gap-1 lg:flex">
            <NavLink to="/" className={navLinkClass} end>
              Acasă
            </NavLink>
            <NavLink to="/products" className={navLinkClass}>
              Produse
            </NavLink>
            {isAuthenticated && (
              <NavLink to="/orders" className={navLinkClass}>
                Comenzile mele
              </NavLink>
            )}
            {isAdmin && (
              <NavLink to="/admin" className={navLinkClass}>
                Admin
              </NavLink>
            )}
          </div>

          {/* Search takes every pixel the rail does not need. */}
          <div className="ml-auto hidden min-w-0 flex-1 justify-end md:flex">
            <NeonSearch className="w-full max-w-md" />
          </div>

          <div className="ml-auto flex shrink-0 items-center gap-2 md:ml-0">
            <Link
              to="/cart"
              aria-label={`Coș de cumpărături, ${totalItems} produse`}
              className="relative grid h-10 w-10 place-items-center rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.05)] text-white transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.5)] hover:shadow-glow-aqua"
            >
              <GeoIcon name="cart" className="h-5 w-5" accent="var(--xx-cyan)" />
              {totalItems > 0 && (
                <span className="absolute -right-1.5 -top-1.5 grid h-5 min-w-[1.25rem] place-items-center rounded-full bg-xx-aqua px-1 text-[0.65rem] font-bold text-[#04050c] shadow-glow-aqua">
                  {totalItems > 99 ? '99+' : totalItems}
                </span>
              )}
            </Link>

            {isAuthenticated ? (
              <div className="hidden items-center gap-2 lg:flex">
                <span className="max-w-[9rem] truncate text-sm xx-ink-muted">
                  Salut, <span className="font-semibold text-white">{user?.fullName?.split(' ')[0]}</span>
                </span>
                <NeonButton
                  variant="secondary"
                  size="sm"
                  onClick={handleLogout}
                  icon={<GeoIcon name="arrow" className="h-3.5 w-3.5" accent="currentColor" />}
                >
                  Ieșire
                </NeonButton>
              </div>
            ) : (
              <div className="hidden items-center gap-2 lg:flex">
                <NeonButton
                  to="/login"
                  variant="secondary"
                  size="sm"
                  icon={<GeoIcon name="user" className="h-3.5 w-3.5" accent="currentColor" />}
                >
                  Login
                </NeonButton>
                <NeonButton
                  to="/register"
                  size="sm"
                  icon={<GeoIcon name="sparkle" className="h-3.5 w-3.5" accent="currentColor" />}
                >
                  Înregistrare
                </NeonButton>
              </div>
            )}

            <button
              className="grid h-10 w-10 place-items-center rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.05)] text-white transition-all duration-xx ease-xx hover:border-[rgba(46,123,255,0.5)] lg:hidden"
              onClick={() => setOpen((value) => !value)}
              aria-label={open ? 'Închide meniul' : 'Deschide meniul'}
              aria-expanded={open}
            >
              <GeoIcon name={open ? 'close' : 'menu'} className="h-5 w-5" accent="var(--xx-cyan)" />
            </button>
          </div>
        </nav>

        {/* Mobile search — below the capsule, always visible, since search is a
            primary action on a phone and must not hide inside a menu. */}
        <div className="mx-auto mt-2 max-w-[1680px] md:hidden">
          <NeonSearch compact />
        </div>
      </header>

      {/* Expanding menu sheet */}
      {open ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            aria-label="Închide meniul"
            className="absolute inset-0 bg-[rgba(4,5,12,0.72)] backdrop-blur-xxs"
            onClick={() => setOpen(false)}
          />
          <div className="absolute inset-x-3 top-3 max-h-[92vh] overflow-y-auto rounded-2xl border border-[rgba(255,255,255,0.14)] bg-[rgba(7,8,24,0.95)] p-4 shadow-glass-lg backdrop-blur-glass-xl animate-xx-materialize">
            <div className="mb-4 flex items-center justify-between">
              <span className="xx-eyebrow mb-0">Navigație</span>
              <button
                onClick={() => setOpen(false)}
                aria-label="Închide meniul"
                className="grid h-9 w-9 place-items-center rounded-xl border border-[rgba(255,255,255,0.12)] bg-white/5 text-white"
              >
                <GeoIcon name="close" className="h-4 w-4" accent="var(--xx-cyan)" />
              </button>
            </div>

            <div className="flex flex-col gap-1">
              <NavLink to="/" className={sheetLinkClass} end>
                <GeoIcon name="home" className="h-5 w-5" accent="var(--xx-cyan)" />
                Acasă
              </NavLink>
              <NavLink to="/products" className={sheetLinkClass}>
                <GeoIcon name="grid" className="h-5 w-5" accent="var(--xx-cyan)" />
                Produse
              </NavLink>
              <NavLink to="/cart" className={sheetLinkClass}>
                <GeoIcon name="cart" className="h-5 w-5" accent="var(--xx-cyan)" />
                Coș {totalItems > 0 ? <span className="badge badge-aqua ml-auto">{totalItems}</span> : null}
              </NavLink>
              {isAuthenticated && (
                <NavLink to="/orders" className={sheetLinkClass}>
                  <GeoIcon name="box" className="h-5 w-5" accent="var(--xx-cyan)" />
                  Comenzile mele
                </NavLink>
              )}
              {isAdmin && (
                <NavLink to="/admin" className={sheetLinkClass}>
                  <GeoIcon name="chart" className="h-5 w-5" accent="var(--xx-purple)" />
                  Panou admin
                </NavLink>
              )}
            </div>

            <div className="xx-divider my-4" />

            {isAuthenticated ? (
              <div className="space-y-3">
                <p className="px-1 text-sm xx-ink-muted">
                  Autentificat ca <span className="font-semibold text-white">{user?.fullName}</span>
                </p>
                <NeonButton
                  variant="secondary"
                  block
                  onClick={handleLogout}
                  icon={<GeoIcon name="arrow" className="h-4 w-4" accent="currentColor" />}
                >
                  Ieșire
                </NeonButton>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                <NeonButton
                  to="/login"
                  variant="secondary"
                  block
                  icon={<GeoIcon name="user" className="h-4 w-4" accent="currentColor" />}
                >
                  Login
                </NeonButton>
                <NeonButton
                  to="/register"
                  block
                  pulse
                  icon={<GeoIcon name="sparkle" className="h-4 w-4" accent="currentColor" />}
                >
                  Înregistrare
                </NeonButton>
              </div>
            )}
          </div>
        </div>
      ) : null}

      {/* Mobile bottom bar — four fixed, thumb-reachable destinations. */}
      <nav
        aria-label="Navigație rapidă"
        className="fixed inset-x-0 bottom-0 z-30 flex border-t border-[rgba(255,255,255,0.12)] bg-[rgba(7,8,24,0.9)] pb-[env(safe-area-inset-bottom)] backdrop-blur-glass-xl sm:hidden"
      >
        <NavLink to="/" className={bottomLinkClass} end>
          <GeoIcon name="home" className="h-5 w-5" accent="currentColor" />
          Acasă
        </NavLink>
        <NavLink to="/products" className={bottomLinkClass}>
          <GeoIcon name="grid" className="h-5 w-5" accent="currentColor" />
          Produse
        </NavLink>
        <NavLink to="/cart" className={bottomLinkClass}>
          <span className="relative">
            <GeoIcon name="cart" className="h-5 w-5" accent="currentColor" />
            {totalItems > 0 ? (
              <span className="absolute -right-2 -top-1.5 grid h-4 min-w-[1rem] place-items-center rounded-full bg-xx-aqua px-1 text-[0.55rem] font-bold text-[#04050c]">
                {totalItems > 9 ? '9+' : totalItems}
              </span>
            ) : null}
          </span>
          Coș
        </NavLink>
        <NavLink to={isAuthenticated ? '/orders' : '/login'} className={bottomLinkClass}>
          <GeoIcon name="user" className="h-5 w-5" accent="currentColor" />
          {isAuthenticated ? 'Comenzi' : 'Cont'}
        </NavLink>
      </nav>
    </>
  );
}
