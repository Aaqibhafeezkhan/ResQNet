export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'emergency';
export type ButtonSize = 'default' | 'large';

const BASE =
  'group/button inline-flex items-center justify-center gap-2 rounded-full px-5 font-medium ' +
  'relative overflow-hidden transition-[background-color,border-color,color,transform,box-shadow] ' +
  'duration-300 ease-cinematic active:scale-[0.97] ' +
  'min-h-[46px] disabled:cursor-not-allowed disabled:opacity-45 focus-visible:outline-none ' +
  'focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 ' +
  'focus-visible:ring-offset-base-950';

/*
  A sheen that sweeps across a button when the pointer enters it.

  It is a pseudo-element rather than an extra element so it does not affect
  the accessible name or the layout, and it is suppressed for visitors who
  asked for reduced motion by the global rule in index.css.
*/
const SHEEN =
  'before:pointer-events-none before:absolute before:inset-y-0 before:-left-3/4 ' +
  'before:w-1/2 before:skew-x-[-18deg] before:bg-white/20 before:opacity-0 ' +
  'before:transition-[left,opacity] duration-[900ms] ease-cinematic ' +
  'hover:before:left-[110%] hover:before:opacity-100 ' +
  'focus-visible:before:left-[110%] focus-visible:opacity-100';

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'bg-network text-base-950 hover:bg-[#82f0eb] shadow-[0_12px_40px_-18px_rgba(92,225,223,0.7)]',
  secondary:
    'border border-white/15 bg-white/[0.025] text-ink-primary hover:border-white/30 hover:bg-white/[0.06]',
  ghost:
    'bg-transparent text-ink-secondary hover:bg-white/[0.04] hover:text-ink-primary',
  emergency:
    'bg-emergency text-base-950 hover:bg-[#ff7b58] ' +
    'shadow-[0_0_0_1px_rgba(255,92,53,0.45),0_12px_40px_-18px_rgba(255,92,53,0.85)]',
};

const SIZES: Record<ButtonSize, string> = {
  default: 'text-[13px]',
  large: 'min-h-[52px] px-6 text-[14px]',
};

export function buttonClasses(
  variant: ButtonVariant = 'primary',
  size: ButtonSize = 'default',
  className = '',
): string {
  const sheen = variant === 'ghost' ? '' : SHEEN;
  return `${BASE} ${sheen} ${VARIANTS[variant]} ${SIZES[size]} ${className}`;
}
