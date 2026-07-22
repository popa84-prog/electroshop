/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        // 2026 palette: Emerald Ink (primary), Champagne (warm accent), Graphite (neutral).
        // `brand` = Emerald Ink so every existing brand-* usage becomes emerald.
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
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
