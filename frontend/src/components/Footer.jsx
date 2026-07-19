export default function Footer() {
  return (
    <footer className="mt-auto border-t border-slate-200 bg-white">
      <div className="mx-auto max-w-7xl px-4 py-8">
        <div className="grid gap-6 sm:grid-cols-3">
          <div>
            <h3 className="flex items-center gap-2 text-lg font-bold text-brand-700">⚡ ElectroShop</h3>
            <p className="mt-2 text-sm text-slate-500">
              Magazinul tău de electronice: telefoane, laptopuri, audio și accesorii.
            </p>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-slate-700">Linkuri</h4>
            <ul className="mt-2 space-y-1 text-sm text-slate-500">
              <li>Acasă</li>
              <li>Produse</li>
              <li>Comenzi</li>
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-slate-700">Contact</h4>
            <ul className="mt-2 space-y-1 text-sm text-slate-500">
              <li>support@electroshop.com</li>
              <li>+40 700 000 000</li>
            </ul>
          </div>
        </div>
        <p className="mt-6 border-t border-slate-100 pt-4 text-center text-xs text-slate-400">
          © {new Date().getFullYear()} ElectroShop. Toate drepturile rezervate.
        </p>
      </div>
    </footer>
  );
}
