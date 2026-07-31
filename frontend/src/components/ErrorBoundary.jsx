import { Component } from 'react';

/**
 * Feature #7 (performance/resilience): catches a rendering error in whatever
 * it wraps and shows a recoverable fallback instead of the entire app going
 * blank. React error boundaries must be class components — there is no hook
 * equivalent for `getDerivedStateFromError`/`componentDidCatch`.
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
      <div className="flex min-h-[16rem] flex-col items-center justify-center gap-3 rounded-xl border border-red-200 bg-red-50 p-8 text-center">
        <p className="text-sm font-semibold text-red-700">A apărut o eroare la afișarea acestei pagini.</p>
        <p className="max-w-md text-xs text-red-600">
          {this.state.error?.message || 'Eroare necunoscută.'}
        </p>
        <div className="flex gap-2">
          <button type="button" className="btn-secondary" onClick={this.handleReset}>
            Încearcă din nou
          </button>
          <button type="button" className="btn-primary" onClick={() => window.location.reload()}>
            Reîncarcă pagina
          </button>
        </div>
      </div>
    );
  }
}
