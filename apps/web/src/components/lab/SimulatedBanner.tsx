import { FlaskConical } from 'lucide-react';

interface SimulatedBannerProps {
  /** A sentence about where the data in this particular view came from. */
  detail?: string;
  className?: string;
}

/**
 * The boundary label.
 *
 * <p>Every simulation view in the Lab carries this text. It is deliberately
 * not a dismissible tooltip and not a footnote in small grey type: a reader
 * who screenshots the result should carry the boundary with the numbers.
 */
export function SimulatedBanner({ detail, className = '' }: SimulatedBannerProps) {
  return (
    <div
      role="note"
      className={
        'flex items-start gap-3 rounded-xl border border-pending/35 bg-pending/[0.06] px-4 py-3 ' +
        className
      }
    >
      <FlaskConical
        size={16}
        className="mt-0.5 shrink-0 text-pending"
        aria-hidden="true"
      />
      <div>
        <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-pending">
          Simulated — not real radio telemetry
        </p>
        <p className="mt-1 text-[12px] leading-relaxed text-ink-secondary">
          {detail ??
            'Every figure below comes from a recorded run of the deterministic ResQNet Java simulator. Nothing here measures a physical radio, an Android handset or a field deployment.'}
        </p>
      </div>
    </div>
  );
}
