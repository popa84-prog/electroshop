import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import productService from '../api/productService';
import GeoIcon from './xxii/GeoIcon';
import Reveal from './xxii/Reveal';

/**
 * XXII — TASK 4 (modular footer: floating cards, geometric icons, links glowing
 * on hover).
 *
 * The footer is not a bar with three columns of text; it is four floating
 * modules on the same grid vocabulary as the rest of the site, so the page ends
 * in the same visual language it began in.
 *
 * Every link uses the same hover treatment: a geometric bullet that lights up
 * and a label that brightens, with the accent glow carried by the bullet rather
 * than by the text. Glowing the text itself hurts legibility at small sizes,
 * which is exactly where footer links live.
 */

const FooterLink = ({ to, href, children }) => {
  const inner = (
    <>
      <span
        aria-hidden="true"
        className="inline-block h-1.5 w-1.5 rotate-45 rounded-[1px] bg-[rgba(255,255,255,0.25)] transition-all duration-xx ease-xx group-hover:bg-[color:var(--xx-cyan)] group-hover:shadow-[0_0_10px_2px_rgba(34,232,245,0.8)]"
      />
      <span className="transition-colors duration-xx ease-xx group-hover:text-white">{children}</span>
    </>
  );

  const className = 'group inline-flex items-center gap-2.5 text-sm xx-ink-muted';

  if (href) {
    return (
      <a href={href} target={href.startsWith('http') ? '_blank' : undefined} rel="noreferrer" className={className}>
        {inner}
      </a>
    );
  }

  return (
    <Link to={to} className={className}>
      {inner}
    </Link>
  );
};

const FooterModule = ({ icon, title, accent, children, delay = 0 }) => (
  <Reveal delay={delay} className="h-full">
    <div className="card card-static h-full p-5">
      <div className="mb-4 flex items-center gap-2.5">
        <span
          aria-hidden="true"
          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.05)]"
        >
          <GeoIcon name={icon} className="h-4 w-4" accent={accent} />
        </span>
        <h4 className="text-xs font-semibold uppercase tracking-[0.16em] text-[color:var(--xx-ink)]">{title}</h4>
      </div>
      {children}
    </div>
  </Reveal>
);

export default function Footer() {
  const [company, setCompany] = useState(null);

  useEffect(() => {
    let cancelled = false;
    productService
      .companyInfo()
      .then((data) => {
        if (!cancelled) setCompany(data);
      })
      .catch(() => {
        if (!cancelled) setCompany(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const name = company?.legalName || 'ElectroShop';
  const addressParts = [company?.address, company?.city, company?.county, company?.country].filter(Boolean);
  const hasContact = Boolean(company?.email || company?.phone || company?.website || addressParts.length);

  return (
    <footer className="relative mt-16 pb-24 sm:pb-10">
      {/* A single hairline of light where the page content ends — the horizon
          line of the deep-space canvas. */}
      <div
        aria-hidden="true"
        className="mx-auto mb-10 h-px max-w-[1680px] bg-gradient-to-r from-transparent via-[rgba(34,232,245,0.45)] to-transparent"
      />

      <div className="mx-auto max-w-[1680px] px-4 sm:px-6">
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <FooterModule icon="bolt" title="Despre" accent="var(--xx-blue)" delay={0}>
            <p className="font-display text-lg font-bold xx-text-gradient">{name}</p>
            <p className="mt-2 text-sm xx-ink-muted">
              Magazinul tău de electronice: telefoane, laptopuri, audio și accesorii.
            </p>
          </FooterModule>

          <FooterModule icon="grid" title="Navigație" accent="var(--xx-cyan)" delay={70}>
            <ul className="space-y-2.5">
              <li>
                <FooterLink to="/">Acasă</FooterLink>
              </li>
              <li>
                <FooterLink to="/products">Produse</FooterLink>
              </li>
              <li>
                <FooterLink to="/cart">Coșul meu</FooterLink>
              </li>
              <li>
                <FooterLink to="/orders">Comenzi</FooterLink>
              </li>
            </ul>
          </FooterModule>

          <FooterModule icon="globe" title="Contact" accent="var(--xx-purple)" delay={140}>
            <ul className="space-y-2.5">
              {company?.email && (
                <li>
                  <FooterLink href={`mailto:${company.email}`}>{company.email}</FooterLink>
                </li>
              )}
              {company?.phone && (
                <li>
                  <FooterLink href={`tel:${company.phone}`}>{company.phone}</FooterLink>
                </li>
              )}
              {company?.website && (
                <li>
                  <FooterLink href={company.website}>{company.website}</FooterLink>
                </li>
              )}
              {addressParts.length > 0 && <li className="text-sm xx-ink-muted">{addressParts.join(', ')}</li>}
              {!hasContact && <li className="text-sm xx-ink-dim">Completează datele în Admin → Date firmă.</li>}
            </ul>
          </FooterModule>

          <FooterModule icon="shield" title="Garanții" accent="var(--xx-lime)" delay={210}>
            <ul className="space-y-3">
              <li className="flex items-start gap-2.5 text-sm xx-ink-muted">
                <GeoIcon name="truck" className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-cyan)" />
                Transport gratuit la orice comandă
              </li>
              <li className="flex items-start gap-2.5 text-sm xx-ink-muted">
                <GeoIcon name="shield" className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-lime)" />
                Garanție conform legislației în vigoare
              </li>
              <li className="flex items-start gap-2.5 text-sm xx-ink-muted">
                <GeoIcon name="refresh" className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-amber)" />
                Retur în 14 zile
              </li>
            </ul>
          </FooterModule>
        </div>

        <p className="mt-10 text-center text-xs xx-ink-dim">
          © {new Date().getFullYear()} {name}. Toate drepturile rezervate.
        </p>
      </div>
    </footer>
  );
}
