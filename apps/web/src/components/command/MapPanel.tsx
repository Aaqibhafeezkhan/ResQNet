import { useEffect, useRef, useState } from 'react';
import { Layers, MapPin, MapPinOff } from 'lucide-react';

import { StatusChip } from '@/components/primitives/StatusChip';
import type { IncidentSummary } from '@/lib/api';

interface MapPanelProps {
  incidents: IncidentSummary[];
  selected: string | null;
  onSelect: (reference: string) => void;
  /**
   * Optional. Supplied so keyboard navigation on the canvas can move the
   * selection highlight as the user arrows through pins, rather than leaving
   * them pressing Enter blind.
   */
  onFocusChange?: (reference: string) => void;
}

function hasSharedCoordinates(incident: IncidentSummary): boolean {
  const { locationShared, latitude, longitude } = incident;
  return locationShared === true &&
    typeof latitude === 'number' && Number.isFinite(latitude) && Math.abs(latitude) <= 90 &&
    typeof longitude === 'number' && Number.isFinite(longitude) && Math.abs(longitude) <= 180;
}

/**
 * Incident locations.
 *
 * <p>Renders on a plain canvas rather than a map library, and the reason is
 * worth stating: most deployments of this dashboard are on a machine with no
 * internet, which is the situation the whole product exists for. A tile server
 * dependency would produce a grey rectangle exactly when it matters.
 *
 * <p>Locations come from devices that explicitly attached one. An incident with
 * no shared location is drawn in the margin, never interpolated to the nearest
 * known point, because a plausible-looking wrong coordinate is far worse than an
 * honest gap.
 */
export function MapPanel({ incidents, selected, onSelect, onFocusChange }: MapPanelProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  /** Keyboard cursor over the pins. Separate from the committed selection. */
  const cursorRef = useRef(0);
  const [showGrid, setShowGrid] = useState(true);

  const located = incidents.filter(hasSharedCoordinates);
  const unlocated = incidents.filter((incident) => !hasSharedCoordinates(incident));

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const context = canvas.getContext('2d');
    if (!context) return undefined;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const render = () => {
      const rect = canvas.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;
      canvas.width = Math.round(rect.width * dpr);
      canvas.height = Math.round(rect.height * dpr);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      const w = rect.width;
      const h = rect.height;

      context.fillStyle = '#07090B';
      context.fillRect(0, 0, w, h);

      if (showGrid) {
        context.strokeStyle = 'rgba(42, 50, 59, 0.55)';
        context.lineWidth = 1;
        const spacing = 36;
        for (let x = spacing; x < w; x += spacing) {
          context.beginPath();
          context.moveTo(x, 0);
          context.lineTo(x, h);
          context.stroke();
        }
        for (let y = spacing; y < h; y += spacing) {
          context.beginPath();
          context.moveTo(0, y);
          context.lineTo(w, y);
          context.stroke();
        }
      }

      if (located.length === 0) return;

      // Equirectangular projection scaled to the incident bounding box. A
      // regional view is what a responder has, and this keeps the relative
      // geometry honest without pretending to be a global projection.
      const lats = located.map((incident) => incident.latitude ?? 0);
      const lons = located.map((incident) => incident.longitude ?? 0);
      const minLat = Math.min(...lats);
      const maxLat = Math.max(...lats);
      const minLon = Math.min(...lons);
      const maxLon = Math.max(...lons);
      const padLat = Math.max((maxLat - minLat) * 0.2, 0.002);
      const padLon = Math.max((maxLon - minLon) * 0.2, 0.002);

      for (const incident of located) {
        const x =
          ((incident.longitude ?? 0 - (minLon - padLon)) / (maxLon + padLon - (minLon - padLon))) *
          w;
        const y =
          ((maxLat + padLat - (incident.latitude ?? 0)) / (maxLat - minLat + padLat * 2)) * h;
        const active = incident.reference === selected;
        const colour = incident.severity >= 3 ? '#E03B3B' : '#E8A33D';

        if (active) {
          context.beginPath();
          context.arc(x, y, 16, 0, Math.PI * 2);
          context.strokeStyle = colour;
          context.lineWidth = 1.5;
          context.stroke();
        }

        context.beginPath();
        context.arc(x, y, active ? 6 : 4.5, 0, Math.PI * 2);
        context.fillStyle = colour;
        context.fill();

        context.fillStyle = '#F3F6F8';
        context.font = '600 10px ui-monospace, monospace';
        context.fillText(incident.reference, x + 10, y + 3);

        // The hit target is drawn larger than the marker so the marker is
        // genuinely clickable at this size.
        context.save();
        context.fillStyle = 'transparent';
        context.beginPath();
        context.arc(x, y, 16, 0, Math.PI * 2);
        context.fill();
        context.restore();
        (context as CanvasRenderingContext2D & { __hit?: Array<{ x: number; y: number; ref: string }> }).__hit =
          [
            ...((context as CanvasRenderingContext2D & { __hit?: Array<{ x: number; y: number; ref: string }> }).__hit ?? []),
            { x, y, ref: incident.reference },
          ];
      }
    };

    render();
    const observer = new ResizeObserver(render);
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [located, selected, showGrid]);

  /**
   * Selects a pin from the keyboard.
   *
   * <p>Arrow keys move between pins in the same order the queue lists them, and
   * Enter or Space selects the one under the cursor. Without this the map was a
   * pointer-only control wearing an `img` role.
   */
  const handleKeyDown = (event: React.KeyboardEvent<HTMLCanvasElement>) => {
    if (located.length === 0) return;
    const forward = event.key === 'ArrowRight' || event.key === 'ArrowDown';
    const backward = event.key === 'ArrowLeft' || event.key === 'ArrowUp';
    if (!forward && !backward && event.key !== 'Enter' && event.key !== ' ') return;

    event.preventDefault();
    if (forward || backward) {
      const step = forward ? 1 : -1;
      const next = (cursorRef.current + step + located.length) % located.length;
      cursorRef.current = next;
      const incident = located[next];
      if (incident) onFocusChange?.(incident.reference);
      return;
    }
    const incident = located[cursorRef.current];
    if (incident) onSelect(incident.reference);
  };

  const handleClick = (event: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const hits = (canvas as HTMLCanvasElement & { __hit?: Array<{ x: number; y: number; ref: string }> }).__hit ?? [];
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    for (const hit of hits) {
      if (Math.hypot(hit.x - x, hit.y - y) <= 16) {
        onSelect(hit.ref);
        return;
      }
    }
  };

  return (
    <section className="surface overflow-hidden" aria-label="Incident locations">
      <header className="flex items-center justify-between gap-3 border-b border-base-700 px-5 py-4">
        <h2 className="flex items-center gap-2 text-sm font-medium text-ink-primary">
          <MapPin size={14} className="text-network" aria-hidden="true" />
          Shared locations
        </h2>
        <button
          type="button"
          onClick={() => setShowGrid((value) => !value)}
          aria-pressed={showGrid}
          className="inline-flex min-h-[34px] items-center gap-1.5 rounded-lg border border-base-600 px-2.5 text-[11px] font-medium text-ink-tertiary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
        >
          <Layers size={12} aria-hidden="true" />
          Grid
        </button>
      </header>

      <div className="relative aspect-[16/9] w-full">
        {located.length === 0 ? (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-8 text-center">
            <MapPinOff size={18} className="text-ink-tertiary" aria-hidden="true" />
            <p className="text-sm text-ink-secondary">No locations shared</p>
            <p className="max-w-xs text-xs leading-relaxed text-ink-tertiary">
              Location is off by default and attached only when a sender
              explicitly includes it on a specific message.
            </p>
          </div>
        ) : null}
        <canvas
          ref={canvasRef}
          onClick={handleClick}
          onKeyDown={handleKeyDown}
          // role="img" is a non-interactive role, so attaching a click handler
          // to it left the pins unreachable by keyboard and invisible to
          // assistive technology. listbox makes the pins enumerable and gives
          // arrow-key navigation a meaning.
          role="listbox"
          tabIndex={0}
          aria-label={`Map of ${located.length} incident locations shared by senders`}
          className="h-full w-full cursor-crosshair focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
        />
        {/*
          The canvas cannot expose its own pins. This is the accessible
          equivalent: the same incidents, reachable by keyboard and announced
          by a screen reader. The queue list already provides this route, so
          this is redundancy rather than the only way in.
        */}
        <ul className="sr-only" aria-label="Incidents plotted on the map">
          {located.map((incident) => (
            <li key={incident.reference}>
              <button
                type="button"
                onClick={() => onSelect(incident.reference)}
                className="focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-10 focus:rounded-lg focus:bg-base-800 focus:px-3 focus:py-2 focus:text-sm focus:text-ink-primary"
              >
                {incident.reference}, {incident.categoryLabel}, severity{' '}
                {incident.severity}
              </button>
            </li>
          ))}
        </ul>
      </div>

      {unlocated.length > 0 ? (
        <div className="flex items-center gap-2 border-t border-base-700 px-5 py-3">
          <StatusChip tone="neutral">{unlocated.length} without location</StatusChip>
          <span className="text-[11px] text-ink-tertiary">
            Not plotted. A missing coordinate is reported, never guessed.
          </span>
        </div>
      ) : null}
    </section>
  );
}
