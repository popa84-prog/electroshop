import { Component } from 'react';
import GeoIcon from './xxii/GeoIcon';
import NeonButton from './xxii/NeonButton';

/**
 * Feature #7 (performance/resilience): catches a rendering error in whatever
 * it wraps and shows a recoverable fallback instead of the entire app going
 * blank. React error boundaries must be class components — there is no hook
 * equivalent for `getDerivedStateFromError`/`componentDidCatch`.
 *
 * XXII — TASK 1 / TASK 6. Panoul de rezervă purta `border-red-200 bg-red-50`
 * cu text `text-red-700` — o combinație gândită pentru fundal alb, care pe
 * suprafața întunecată devenea un bloc palid. Acum folosește exact aceleași
 * jetoane de sticlă roșie ca insignele critice din `utils/auditLabels.js`, deci
 * un incident arată la fel oriunde apare în aplicație.
 *
 * Trei corecții dincolo de culoare:
 *
 *   1. **`role="alert"`** — un ecran care înlocuiește conținutul așteptat
 *      trebuie anunțat imediat, nu descoperit prin tabulare.
 *   2. **Mesajul tehnic stă într-un `<pre>` monospațiat.** Un mesaj de eroare
 *      conține adesea nume de proprietăți și paranteze; fontul proporțional le
 *      face greu de copiat corect într-un raport.
 *   3. **Butoanele sunt `NeonButton`.** „Încearcă din nou” este acțiunea
 *      necostisitoare și rămâne discretă (`ghost`); „Reîncarcă pagina” aruncă
 *      starea aplicației, deci poartă tratamentul `danger`.
 *
 * Fiind componentă de clasă, nu are acces la hook-uri — dar `GeoIcon` și
 * `NeonButton` sunt componente obișnuite și se compun fără nicio restricție.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', error, info?.componentStack);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    if (this.props.fallback) {
      return this.props.fallback(this.state.error, this.handleReset);
    }

    return (
      <div
        role="alert"
        className="flex min-h-[16rem] flex-col items-center justify-center gap-3 rounded-[1.15rem] border border-[rgba(255,90,122,0.45)] bg-[rgba(255,90,122,0.08)] p-8 text-center backdrop-blur-xl"
        style={{ boxShadow: '0 0 52px -18px rgba(255,90,122,0.55)' }}
      >
        <span className="flex h-12 w-12 items-center justify-center rounded-full border border-[rgba(255,90,122,0.5)] bg-[rgba(255,90,122,0.14)]">
          <GeoIcon name="alert" className="h-6 w-6" accent="#ff8fa8" />
        </span>

        <p className="text-sm font-semibold text-[#ff8fa8]">
          A apărut o eroare la afișarea acestei pagini.
        </p>

        <pre className="max-w-md whitespace-pre-wrap break-words rounded-[0.8rem] border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] px-3 py-2 font-mono text-xs xx-ink-muted">
          {this.state.error?.message || 'Eroare necunoscută.'}
        </pre>

        <div className="mt-1 flex flex-wrap justify-center gap-2">
          <NeonButton
            variant="ghost"
            size="sm"
            onClick={this.handleReset}
            icon={<GeoIcon name="refresh" className="h-4 w-4" accent="currentColor" />}
          >
            Încearcă din nou
          </NeonButton>
          <NeonButton
            variant="danger"
            size="sm"
            onClick={() => window.location.reload()}
            icon={<GeoIcon name="bolt" className="h-4 w-4" accent="currentColor" />}
          >
            Reîncarcă pagina
          </NeonButton>
        </div>
      </div>
    );
  }
}
