import { ShieldAlert } from 'lucide-react';
import type { ReactNode } from 'react';

interface SafetyNoteProps {
  title?: string;
  children: ReactNode;
  className?: string;
}

export function SafetyNote({
  title = 'Read before relying on this',
  children,
  className = '',
}: SafetyNoteProps) {
  return (
    <div
      className={
        'flex items-start gap-3 rounded-xl border border-pending/25 bg-pending-soft px-4 py-3.5 ' +
        className
      }
    >
      <ShieldAlert size={16} className="mt-0.5 shrink-0 text-pending" aria-hidden="true" />
      <div>
        <p className="text-[12px] font-semibold text-ink-primary">{title}</p>
        <div className="mt-1 text-[12px] leading-relaxed text-ink-secondary">{children}</div>
      </div>
    </div>
  );
}
