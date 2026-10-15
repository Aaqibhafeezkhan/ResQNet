import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'emergency';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  children: ReactNode;
}

/**
 * One button implementation with four intents.
 *
 * Focus-visible rings are never removed, and the minimum height is 44px, which
 * is the smallest target that is reliably tappable. On a page used at 3am by
 * someone who is not looking carefully, small targets are a real cost.
 */
export function Button({ variant = 'primary', children, className = '', ...rest }: ButtonProps) {
  const base =
    'inline-flex items-center justify-center gap-2 rounded-xl px-5 font-medium ' +
    'transition-colors duration-200 ease-precise min-h-[44px] ' +
    'disabled:cursor-not-allowed disabled:opacity-45 focus-visible:outline-none ' +
    'focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 ' +
    'focus-visible:ring-offset-base-900';

  const variants: Record<Variant, string> = {
    primary:
      'bg-network text-base-900 hover:bg-[#4ee2f0] active:bg-[#26bccd]',
    secondary:
      'bg-base-700 text-ink-primary hover:bg-base-600 border border-base-500',
    ghost:
      'bg-transparent text-ink-secondary hover:text-ink-primary hover:bg-base-800',
    emergency:
      'bg-emergency text-ink-primary hover:bg-emergency-pressed ' +
      'shadow-[0_0_0_1px_rgba(224,59,59,0.5),0_10px_34px_-16px_rgba(224,59,59,0.9)]',
  };

  return (
    <button type="button" className={`${base} ${variants[variant]} ${className}`} {...rest}>
      {children}
    </button>
  );
}
