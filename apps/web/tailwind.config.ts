import type { Config } from 'tailwindcss';

/**
 * Design tokens.
 *
 * The palette is intentionally narrow. Deep charcoal surfaces, one emergency
 * red reserved for life-safety actions, cyan for anything the network is
 * actively doing, emerald only for confirmed delivery, and amber for anything
 * waiting. Colour is never the sole carrier of meaning: every state also has a
 * label, because red and green are the one pair a responder with a colour
 * vision deficiency cannot rely on at 4am.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        base: {
          950: '#050708',
          900: '#080B0D',
          850: '#0C1012',
          800: '#111619',
          750: '#151B1E',
          700: '#1C2428',
          600: '#273136',
          500: '#354147',
        },
        ink: {
          primary: '#F2F0E9',
          secondary: '#A9B0AC',
          tertiary: '#818C86',
          disabled: '#4B5550',
        },
        emergency: {
          DEFAULT: '#E74B4B',
          pressed: '#C03434',
          soft: 'rgba(224, 59, 59, 0.14)',
        },
        network: {
          DEFAULT: '#43D8DC',
          soft: 'rgba(67, 216, 220, 0.13)',
        },
        delivered: {
          DEFAULT: '#45D19A',
          soft: 'rgba(69, 209, 154, 0.13)',
        },
        pending: {
          DEFAULT: '#EDB35C',
          soft: 'rgba(237, 179, 92, 0.13)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        display: ['Iowan Old Style', 'Baskerville', 'Palatino Linotype', 'Book Antiqua', 'Georgia', 'serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      letterSpacing: {
        tightest: '-0.035em',
        widest: '0.18em',
      },
      maxWidth: {
        '8xl': '88rem',
      },
      boxShadow: {
        lift: '0 1px 0 rgba(255,255,255,0.04) inset, 0 22px 60px -30px rgba(0,0,0,0.95)',
        glow: '0 0 0 1px rgba(67,216,220,0.24), 0 0 44px -12px rgba(67,216,220,0.38)',
        edge: '0 1px 0 rgba(255,255,255,0.05) inset',
      },
      transitionTimingFunction: {
        // A critically-damped-looking curve. State changes settle rather than
        // bounce, which reads as precise rather than playful.
        precise: 'cubic-bezier(0.22, 1, 0.36, 1)',
      },
      keyframes: {
        'signal-pulse': {
          '0%, 100%': { opacity: '0.25', transform: 'scale(1)' },
          '50%': { opacity: '0.75', transform: 'scale(1.35)' },
        },
        'route-dash': {
          to: { strokeDashoffset: '-40' },
        },
        'rise-in': {
          from: { opacity: '0', transform: 'translate3d(0, 12px, 0)' },
          to: { opacity: '1', transform: 'translate3d(0, 0, 0)' },
        },
      },
      animation: {
        'signal-pulse': 'signal-pulse 2.4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'route-dash': 'route-dash 1.1s linear infinite',
        'rise-in': 'rise-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) both',
      },
    },
  },
  plugins: [],
} satisfies Config;
