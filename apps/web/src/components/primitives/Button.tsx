import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router-dom';

import { Magnetic } from './Magnetic';
import { buttonClasses, type ButtonSize, type ButtonVariant } from './buttonStyles';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  children: ReactNode;
}

export function Button({
  variant = 'primary',
  size = 'default',
  children,
  className = '',
  ...rest
}: ButtonProps) {
  return (
    <Magnetic strength={4} className="rounded-full">
      <button
        type="button"
        className={buttonClasses(variant, size, className)}
        {...rest}
      >
        {children}
      </button>
    </Magnetic>
  );
}

interface ButtonLinkProps extends LinkProps {
  variant?: ButtonVariant;
  size?: ButtonSize;
  children: ReactNode;
}

export function ButtonLink({
  variant = 'primary',
  size = 'default',
  children,
  className = '',
  ...rest
}: ButtonLinkProps) {
  return (
    <Magnetic strength={5} className="rounded-full">
      <Link className={buttonClasses(variant, size, className)} {...rest}>
        {children}
      </Link>
    </Magnetic>
  );
}

interface ButtonAnchorProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  children: ReactNode;
}

export function ButtonAnchor({
  variant = 'primary',
  size = 'default',
  children,
  className = '',
  ...rest
}: ButtonAnchorProps) {
  return (
    <Magnetic strength={5} className="rounded-full">
      <a className={buttonClasses(variant, size, className)} {...rest}>
        {children}
      </a>
    </Magnetic>
  );
}
