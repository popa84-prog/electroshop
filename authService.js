@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  color-scheme: light;
}

body {
  @apply bg-slate-50 text-slate-800 antialiased;
}

/* Reusable component classes */
@layer components {
  .btn {
    @apply inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition focus:outline-none focus:ring-2 focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed;
  }
  .btn-primary {
    @apply btn bg-brand-600 text-white hover:bg-brand-700 focus:ring-brand-500;
  }
  .btn-secondary {
    @apply btn bg-white text-slate-700 border border-slate-300 hover:bg-slate-100 focus:ring-slate-400;
  }
  .btn-danger {
    @apply btn bg-red-600 text-white hover:bg-red-700 focus:ring-red-500;
  }
  .input {
    @apply w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500;
  }
  .card {
    @apply rounded-xl border border-slate-200 bg-white shadow-sm;
  }
  .badge {
    @apply inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium;
  }
}
