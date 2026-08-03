import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Footer from './Footer';
import GeoIcon from './xxii/GeoIcon';
import { useCart } from '../context/CartContext';

/**
 * XXII — TASK 9 (the page shell).
 *
 * The shell owns three things: the floating navbar, the content well, and the
 * toast layer. It deliberately owns no background — the deep-space canvas, the
 * light pools and the technical grid all live on `body` in `index.css`, so
 * every route (including the admin ones, which use their own layout) sits on
 * exactly the same surface.
 *
 * `pb-24 sm:pb-0` on the main element reserves room for the mobile bottom bar,
 * which is fixed and would otherwise cover the last row of content.
 */
export default function Layout() {
  const { notice, clearNotice } = useCart();

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />

      {/* Wider than the default container: the admin tables and the product grid
          both benefit from the extra columns on large monitors, while the cap
          keeps line length readable on ultra-wide displays. */}
      <main className="mx-auto w-full max-w-[1680px] flex-1 px-4 py-6 sm:px-6">
        <Outlet />
      </main>

      <Footer />

      {notice && (
        <div className="pointer-events-none fixed inset-x-0 top-24 z-50 flex justify-center px-4">
          <div
            role="alert"
            className="pointer-events-auto flex items-center gap-3 rounded-2xl border border-[rgba(255,84,112,0.45)] bg-[rgba(30,8,18,0.9)] px-4 py-3 text-sm font-medium text-[#ffc2cc] shadow-[0_0_46px_-12px_rgba(255,84,112,0.8)] backdrop-blur-glass animate-xx-materialize"
          >
            <GeoIcon name="alert" className="h-5 w-5 shrink-0" accent="var(--xx-red)" />
            <span>{notice.text}</span>
            <button
              onClick={clearNotice}
              className="ml-2 grid h-6 w-6 shrink-0 place-items-center rounded-full transition-colors duration-xx hover:bg-white/10 hover:text-white"
              aria-label="Închide"
            >
              <GeoIcon name="close" className="h-3.5 w-3.5" accent="currentColor" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
