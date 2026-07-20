import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Footer from './Footer';
import { useCart } from '../context/CartContext';

export default function Layout() {
  const { notice, clearNotice } = useCart();

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">
        <Outlet />
      </main>
      <Footer />

      {notice && (
        <div className="fixed inset-x-0 top-4 z-50 flex justify-center px-4">
          <div
            role="alert"
            className="flex items-center gap-3 rounded-lg bg-red-600 px-4 py-3 text-sm font-medium text-white shadow-lg"
          >
            <span aria-hidden="true">⚠️</span>
            <span>{notice.text}</span>
            <button
              onClick={clearNotice}
              className="ml-2 text-white/80 hover:text-white"
              aria-label="Închide"
            >
              ✕
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
