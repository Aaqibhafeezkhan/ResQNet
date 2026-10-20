interface BrandMarkProps {
  className?: string;
}

export function BrandMark({ className = '' }: BrandMarkProps) {
  return (
    <svg
      viewBox="0 0 32 32"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <rect width="32" height="32" rx="8" fill="currentColor" opacity="0.08" />
      <path
        d="M16 5.5 7 10.5v9L16 25l9-5.5v-9L16 5.5Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
        opacity="0.42"
      />
      <path
        d="M11.5 14.5c1.2-1.5 2.8-2.3 4.5-2.3s3.3.8 4.5 2.3M13.6 17c.7-.8 1.5-1.2 2.4-1.2s1.7.4 2.4 1.2"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <circle cx="16" cy="19.7" r="1.35" fill="currentColor" />
    </svg>
  );
}
