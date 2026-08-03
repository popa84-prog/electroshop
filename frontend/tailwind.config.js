/** @type {import('tailwindcss').Config} */

/**
 * XXII DESIGN SYSTEM — Tailwind token layer.
 *
 * The palette is split in two generations that coexist on purpose:
 *
 *  1. The 2026 palette (`brand` / `champagne` / `graphite`) is kept EXACTLY as it
 *     was. Roughly 8.400 lines of existing pages reference those scales directly
 *     (`text-graphite-800`, `bg-champagne-100`, `border-graphite-100`, …).
 *     Deleting them would break every screen at once. Instead, `src/index.css`
 *     carries a compatibility layer that re-maps those legacy utilities onto the
 *     dark XXII surface, so untouched pages inherit the new look automatically.
 *
 *  2. The XXII palette (`electric` / `cosmic` / `aqua` / `magenta` / `void`) is
 *     the futuristic system: neon accents rendered on a deep-space background,
 *     glassmorphic surfaces, and edge glow.
 *
 * Everything a component needs — blur radii, glow shadows, motion keyframes,
 * timing, the 12-column modular grid and the TV breakpoint — is declared here so
 * component files never hard-code a magic number.
 */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // ---- Legacy 2026 palette (kept for backwards compatibility) ----
        brand: {
          50: '#ecfbf4',
          100: '#cef3e1',
          200: '#9fe6c6',
          300: '#66d1a6',
          400: '#33b585',
          500: '#14966b',
          600: '#0c7857',
          700: '#0a5f47',
          800: '#0a4b3a',
          900: '#073d30',
        },
        champagne: {
          50: '#fbf7ef',
          100: '#f4ebd8',
          200: '#ead9b6',
          300: '#ddc08a',
          400: '#cda867',
          500: '#bf9a4e',
          600: '#a07e3a',
          700: '#7d6230',
        },
        graphite: {
          50: '#f4f4f5',
          100: '#e5e5e8',
          200: '#cbcbd1',
          300: '#a6a6b0',
          400: '#74747f',
          500: '#52525b',
          600: '#3f3f47',
          700: '#303036',
          800: '#232327',
          900: '#18181b',
        },

        // ---- XXII palette ----
        // Electric Blue — the primary interactive hue.
        electric: {
          50: '#eaf2ff',
          100: '#d0e2ff',
          200: '#a3c6ff',
          300: '#6ea4ff',
          400: '#4189ff',
          500: '#2e7bff',
          600: '#1a5ee6',
          700: '#1147b4',
          800: '#0d3585',
          900: '#0a265e',
        },
        // Cosmic Purple — the secondary hue, used for AI/intelligence surfaces.
        cosmic: {
          50: '#f3ecff',
          100: '#e4d6ff',
          200: '#c9adff',
          300: '#ab7fff',
          400: '#9155ff',
          500: '#7a3cff',
          600: '#6122e0',
          700: '#4b1ab0',
          800: '#371283',
          900: '#260c5c',
        },
        // Aqua / Cyan Glow — success, live data, scan effects.
        aqua: {
          50: '#e6feff',
          100: '#c2fbff',
          200: '#8df6ff',
          300: '#4fefff',
          400: '#22e8f5',
          500: '#00d6e8',
          600: '#00abbd',
          700: '#087f8c',
          800: '#0c5f69',
          900: '#0d4750',
        },
        // Magenta — alerts, promotions, "hot" states.
        magenta: {
          50: '#ffe9f9',
          100: '#ffcff1',
          200: '#ff9fe4',
          300: '#ff6bd6',
          400: '#ff3dcb',
          500: '#ed19b1',
          600: '#c40f92',
          700: '#971073',
          800: '#711059',
          900: '#4e0c3d',
        },
        // Void — the deep-space background scale. 900 is the darkest.
        void: {
          50: '#6a6cb4',
          100: '#4f5194',
          200: '#3a3c77',
          300: '#2a2c5e',
          400: '#1e2049',
          500: '#16183a',
          600: '#10122e',
          700: '#0b0d22',
          800: '#070818',
          900: '#04050c',
        },

        // Named single-tone accents used by gradients and glows.
        'xx-blue': '#2e7bff',
        'xx-purple': '#7a3cff',
        'xx-aqua': '#22e8f5',
        'xx-cyan': '#00f0ff',
        'xx-magenta': '#ff3dcb',
        'xx-violet': '#1a0b3d',
      },

      fontFamily: {
        // Satoshi is listed first so a self-hosted licence drops in without any
        // code change; Inter Tight (loaded in index.html) is the shipped default.
        sans: ['"Inter Tight"', 'Satoshi', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['"Space Grotesk"', 'Satoshi', '"Inter Tight"', 'ui-sans-serif', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },

      // Glassmorphism blur radii — the brief specifies 18–24px.
      backdropBlur: {
        xxs: '4px',
        glass: '18px',
        'glass-lg': '22px',
        'glass-xl': '24px',
      },

      // Edge glow — the brief specifies a 40–60px shadow spread.
      boxShadow: {
        'glow-blue': '0 0 40px -8px rgba(46, 123, 255, 0.55)',
        'glow-purple': '0 0 44px -8px rgba(122, 60, 255, 0.55)',
        'glow-aqua': '0 0 44px -8px rgba(34, 232, 245, 0.5)',
        'glow-magenta': '0 0 44px -8px rgba(255, 61, 203, 0.5)',
        'glow-strong': '0 0 60px -6px rgba(46, 123, 255, 0.75)',
        glass: '0 8px 32px -8px rgba(4, 5, 12, 0.65), inset 0 1px 0 0 rgba(255,255,255,0.08)',
        'glass-lg': '0 20px 60px -18px rgba(4, 5, 12, 0.85), inset 0 1px 0 0 rgba(255,255,255,0.10)',
        float: '0 24px 70px -24px rgba(0, 0, 0, 0.9)',
      },

      backgroundImage: {
        // The three signature gradients from the brief.
        'xx-primary': 'linear-gradient(120deg, #2e7bff 0%, #7a3cff 100%)',
        'xx-aqua': 'linear-gradient(120deg, #22e8f5 0%, #ff3dcb 100%)',
        'xx-deep': 'linear-gradient(120deg, #1a0b3d 0%, #00f0ff 100%)',
        'xx-reactor':
          'radial-gradient(1200px 600px at 15% -10%, rgba(122,60,255,0.28), transparent 60%), radial-gradient(1000px 500px at 85% 0%, rgba(46,123,255,0.22), transparent 60%), radial-gradient(900px 700px at 50% 110%, rgba(34,232,245,0.14), transparent 60%)',
        'xx-grid':
          'linear-gradient(rgba(255,255,255,0.035) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.035) 1px, transparent 1px)',
      },

      backgroundSize: {
        'grid-32': '32px 32px',
        'shift-200': '200% 200%',
      },

      // Modular grid — TASK 9.
      gridTemplateColumns: {
        12: 'repeat(12, minmax(0, 1fr))',
        'tv-6': 'repeat(6, minmax(0, 1fr))',
      },

      // 24–32px module spacing from the brief.
      spacing: {
        module: '1.5rem', // 24px
        'module-lg': '2rem', // 32px
      },

      screens: {
        // Living-room / large-display tier — TV rules in every task.
        tv: '1920px',
      },

      // Global motion timing — TASK 8 specifies 0.15s–0.25s.
      transitionDuration: {
        xxfast: '150ms',
        xx: '200ms',
        xxslow: '250ms',
      },

      transitionTimingFunction: {
        xx: 'cubic-bezier(0.22, 1, 0.36, 1)',
      },

      keyframes: {
        'xx-float': {
          '0%, 100%': { transform: 'translate3d(0, 0, 0)' },
          '50%': { transform: 'translate3d(0, -6px, 0)' },
        },
        'xx-pulse-glow': {
          '0%, 100%': { boxShadow: '0 0 26px -10px rgba(46,123,255,0.65)' },
          '50%': { boxShadow: '0 0 52px -6px rgba(122,60,255,0.85)' },
        },
        'xx-gradient-shift': {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
        'xx-scan': {
          '0%': { transform: 'translateY(-110%)', opacity: '0' },
          '12%': { opacity: '1' },
          '88%': { opacity: '1' },
          '100%': { transform: 'translateY(110%)', opacity: '0' },
        },
        'xx-scan-x': {
          '0%': { transform: 'translateX(-110%)' },
          '100%': { transform: 'translateX(110%)' },
        },
        'xx-materialize': {
          '0%': { opacity: '0', transform: 'translate3d(0, 14px, 0) scale(0.97)', filter: 'blur(6px)' },
          '100%': { opacity: '1', transform: 'translate3d(0, 0, 0) scale(1)', filter: 'blur(0)' },
        },
        'xx-rise': {
          '0%': { opacity: '0', transform: 'translate3d(0, 22px, 0)' },
          '100%': { opacity: '1', transform: 'translate3d(0, 0, 0)' },
        },
        'xx-ripple': {
          '0%': { transform: 'scale(0.6)', opacity: '0.55' },
          '100%': { transform: 'scale(2.4)', opacity: '0' },
        },
        'xx-shake': {
          '0%, 100%': { transform: 'translateX(0)' },
          '20%': { transform: 'translateX(-5px)' },
          '40%': { transform: 'translateX(5px)' },
          '60%': { transform: 'translateX(-3px)' },
          '80%': { transform: 'translateX(3px)' },
        },
        'xx-charge': {
          '0%': { backgroundPosition: '-140% 0' },
          '100%': { backgroundPosition: '240% 0' },
        },
        'xx-spin-slow': {
          '0%': { transform: 'rotate(0deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
        'xx-blink': {
          '0%, 45%': { opacity: '1' },
          '50%, 95%': { opacity: '0.25' },
          '100%': { opacity: '1' },
        },
      },

      animation: {
        'xx-float': 'xx-float 6s ease-in-out infinite',
        'xx-pulse-glow': 'xx-pulse-glow 2.4s ease-in-out infinite',
        'xx-gradient': 'xx-gradient-shift 12s ease infinite',
        'xx-scan': 'xx-scan 2.6s cubic-bezier(0.4, 0, 0.2, 1) infinite',
        'xx-scan-x': 'xx-scan-x 1.8s cubic-bezier(0.4, 0, 0.2, 1) infinite',
        'xx-materialize': 'xx-materialize 250ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'xx-rise': 'xx-rise 250ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'xx-ripple': 'xx-ripple 500ms ease-out forwards',
        'xx-shake': 'xx-shake 250ms ease-in-out 1',
        'xx-charge': 'xx-charge 1.2s linear infinite',
        'xx-spin-slow': 'xx-spin-slow 9s linear infinite',
        'xx-blink': 'xx-blink 1.6s steps(1, end) infinite',
      },
    },
  },
  plugins: [],
};
