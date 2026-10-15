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
          900: '#07090B',
          850: '#0B0E11',
          800: '#11151A',
          750: '#14181D',
          700: '#1B2127',
          600: '#252D36',
          500: '#2A323B',
        },
        ink: {
          primary: '#F3F6F8',
          secondary: '#9BA7B4',
          tertiary: '#6B7885',
          disabled: '#4A555F',
        },
        emergency: {
          DEFAULT: '#E03B3B',
          pressed: '#B72C2C',
          soft: 'rgba(224, 59, 59, 0.14)',
        },
        network: {
          DEFAULT: '#2ED3E6',
          soft: 'rgba(46, 211, 230, 0.14)',
        },
        delivered: {
          DEFAULT: '#35C98A',
          soft: 'rgba(53, 201, 138, 0.14)',
        },
        pending: {
          DEFAULT: '#E8A33D',
          soft: 'rgba(232, 163, 61, 0.14)',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      letterSpacing: {
        tightest: '-0.03em',
        widest: '0.18em',
      },
      boxShadow: {
        lift: '0 1px 0 rgba(255,255,255,0.04) inset, 0 18px 40px -24px rgba(0,0,0,0.9)',
        glow: '0 0 0 1px rgba(46,211,230,0.28), 0 0 28px -8px rgba(46,211,230,0.42)',
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
