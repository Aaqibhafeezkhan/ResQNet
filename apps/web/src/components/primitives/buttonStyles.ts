export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'emergency';
export type ButtonSize = 'default' | 'large';

const BASE =
  'inline-flex items-center justify-center gap-2 rounded-full px-5 font-medium ' +
  'transition-colors duration-200 ease-precise min-h-[44px] ' +
  'disabled:cursor-not-allowed disabled:opacity-45 focus-visible:outline-none ' +
  'focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 ' +
  'focus-visible:ring-offset-base-900';

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'bg-network text-base-950 hover:bg-[#66e6e9] active:bg-[#26bccd] shadow-[0_10px_36px_-16px_rgba(67,216,220,0.65)]',
  secondary:
    'border border-base-500 bg-base-800/70 text-ink-primary hover:border-ink-tertiary hover:bg-base-700',
  ghost:
    'bg-transparent text-ink-secondary hover:bg-base-800 hover:text-ink-primary',
  emergency:
    'bg-emergency text-white hover:bg-emergency-pressed ' +
    'shadow-[0_0_0_1px_rgba(224,59,59,0.5),0_10px_34px_-16px_rgba(224,59,59,0.9)]',
};

const SIZES: Record<ButtonSize, string> = {
  default: 'text-sm',
  large: 'min-h-[50px] px-6 text-[15px]',
};

export function buttonClasses(
  variant: ButtonVariant = 'primary',
  size: ButtonSize = 'default',
  className = '',
): string {
  return `${BASE} ${VARIANTS[variant]} ${SIZES[size]} ${className}`;
}

