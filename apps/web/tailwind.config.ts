import type { Config } from 'tailwindcss';

/**
 * Design tokens.
 *
 * The palette is intentionally narrow: near-black surfaces, a single electric
 * cyan for anything the network is actively doing, warm emergency orange for
 * the sender and for life-safety actions, and a quieter jade for confirmed
 * delivery. Amber is reserved for anything waiting.
 *
 * Colour is never the only carrier of meaning. Every network state also has a
 * word, because red/green colour deficiency is common and this project is
 * safety-adjacent.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        base: {
          950: '#040607',
          900: '#07090a',
          850: '#0a0d0e',
          800: '#0e1213',
          750: '#121718',
          700: '#192021',
          600: '#253030',
          500: '#354140',
        },
        ink: {
          primary: '#F4F1E8',
          secondary: '#A8B1AA',
          tertiary: '#7E8A83',
          disabled: '#4B5550',
        },
        emergency: {
          DEFAULT: '#FF5C35',
          pressed: '#DB421F',
          soft: 'rgba(255, 92, 53, 0.13)',
        },
        network: {
          DEFAULT: '#5CE1DF',
          soft: 'rgba(92, 225, 223, 0.12)',
        },
        delivered: {
          DEFAULT: '#62E6B5',
          soft: 'rgba(98, 230, 181, 0.12)',
        },
        pending: {
          DEFAULT: '#EFB861',
          soft: 'rgba(239, 184, 97, 0.12)',
        },
      },
      fontFamily: {
        sans: [
          'Instrument Sans',
          'Inter',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'sans-serif',
        ],
        display: [
          'Instrument Serif',
          'Iowan Old Style',
          'Baskerville',
          'Palatino Linotype',
          'Georgia',
          'serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      letterSpacing: {
        tightest: '-0.045em',
        widest: '0.2em',
      },
      maxWidth: {
        '8xl': '90rem',
      },
      boxShadow: {
        lift: '0 1px 0 rgba(255,255,255,0.05) inset, 0 28px 80px -36px rgba(0,0,0,0.95)',
        glow: '0 0 0 1px rgba(92,225,223,0.22), 0 0 52px -14px rgba(92,225,223,0.4)',
        edge: '0 1px 0 rgba(255,255,255,0.05) inset',
      },
      transitionTimingFunction: {
        precise: 'cubic-bezier(0.22, 1, 0.36, 1)',
        cinematic: 'cubic-bezier(0.16, 1, 0.3, 1)',
      },
      keyframes: {
        'signal-pulse': {
          '0%, 100%': { opacity: '0.22', transform: 'scale(1)' },
          '50%': { opacity: '0.7', transform: 'scale(1.4)' },
        },
        'route-dash': {
          to: { strokeDashoffset: '-48' },
        },
        'rise-in': {
          from: { opacity: '0', transform: 'translate3d(0, 18px, 0)' },
          to: { opacity: '1', transform: 'translate3d(0, 0, 0)' },
        },
        'soft-float': {
          '0%, 100%': { transform: 'translate3d(0, 0, 0)' },
          '50%': { transform: 'translate3d(0, -6px, 0)' },
        },
        scan: {
          from: { transform: 'translateY(-100%)' },
          to: { transform: 'translateY(100%)' },
        },
        marquee: {
          from: { transform: 'translate3d(0, 0, 0)' },
          to: { transform: 'translate3d(-50%, 0, 0)' },
        },
      },
      animation: {
        'signal-pulse': 'signal-pulse 2.8s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'route-dash': 'route-dash 1.4s linear infinite',
        'rise-in': 'rise-in 0.7s cubic-bezier(0.16, 1, 0.3, 1) both',
        'soft-float': 'soft-float 7s ease-in-out infinite',
        scan: 'scan 5.5s linear infinite',
        marquee: 'marquee 38s linear infinite',
      },
    },
  },
  plugins: [],
} satisfies Config;
