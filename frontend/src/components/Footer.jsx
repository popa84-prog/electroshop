import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import productService from '../api/productService';

export default function Footer() {
  const [company, setCompany] = useState(null);

  useEffect(() => {
    productService
      .companyInfo()
      .then(setCompany)
      .catch(() => setCompany(null));
  }, []);

  const name = company?.legalName || 'ElectroShop';
  const addressParts = [company?.address, company?.city, company?.county, company?.country].filter(
    Boolean
  );

  return (
    <footer className="mt-auto border-t border-graphite-100 bg-white">
      <div className="mx-auto max-w-7xl px-4 py-8">
        <div className="grid gap-6 sm:grid-cols-3">
          <div>
            <h3 className="flex items-center gap-2 text-lg font-bold text-brand-700">⚡ {name}</h3>
            <p className="mt-2 text-sm text-graphite-500">
              Magazinul tău de electronice: telefoane, laptopuri, audio și accesorii.
            </p>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-graphite-700">Linkuri</h4>
            <ul className="mt-2 space-y-1 text-sm text-graphite-500">
              <li>
                <Link to="/" className="hover:text-brand-600">
                  Acasă
                </Link>
              </li>
              <li>
                <Link to="/products" className="hover:text-brand-600">
                  Produse
                </Link>
              </li>
              <li>
                <Link to="/orders" className="hover:text-brand-600">
                  Comenzi
                </Link>
              </li>
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-graphite-700">Contact</h4>
            <ul className="mt-2 space-y-1 text-sm text-graphite-500">
              {company?.email && (
                <li>
                  <a href={`mailto:${company.email}`} className="hover:text-brand-600">
                    {company.email}
                  </a>
                </li>
              )}
              {company?.phone && (
                <li>
                  <a href={`tel:${company.phone}`} className="hover:text-brand-600">
                    {company.phone}
                  </a>
                </li>
              )}
              {company?.website && (
                <li>
                  <a
                    href={company.website}
                    target="_blank"
                    rel="noreferrer"
                    className="hover:text-brand-600"
                  >
                    {company.website}
                  </a>
                </li>
              )}
              {addressParts.length > 0 && <li>{addressParts.join(', ')}</li>}
              {!company?.email && !company?.phone && !addressParts.length && (
                <li className="text-graphite-400">
                  Completează datele în Admin → Date firmă.
                </li>
              )}
            </ul>
          </div>
        </div>
        <p className="mt-6 border-t border-graphite-100 pt-4 text-center text-xs text-graphite-400">
          © {new Date().getFullYear()} {name}. Toate drepturile rezervate.
        </p>
      </div>
    </footer>
  );
}
