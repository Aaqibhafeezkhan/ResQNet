export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'emergency';
export type ButtonSize = 'default' | 'large';

const BASE =
  'group/button inline-flex items-center justify-center gap-2 rounded-full px-5 font-medium ' +
  'transition-[background-color,border-color,color,transform,box-shadow] duration-300 ease-cinematic ' +
  'min-h-[46px] disabled:cursor-not-allowed disabled:opacity-45 focus-visible:outline-none ' +
  'focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 ' +
  'focus-visible:ring-offset-base-950';

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
  return `${BASE} ${VARIANTS[variant]} ${SIZES[size]} ${className}`;
}
