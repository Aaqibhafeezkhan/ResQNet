import { useEffect, useRef, useState } from 'react';
import { Layers, MapPin, MapPinOff } from 'lucide-react';

import { StatusChip } from '@/components/primitives/StatusChip';
import type { IncidentSummary } from '@/lib/api';

interface MapPanelProps {
  incidents: IncidentSummary[];
  selected: string | null;
  onSelect: (reference: string) => void;
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
export function MapPanel({ incidents, selected, onSelect }: MapPanelProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [showGrid, setShowGrid] = useState(true);

  const located = incidents.filter(
    (incident) => incident.locationShared && incident.latitude !== null,
  );
  const unlocated = incidents.filter((incident) => !incident.locationShared);

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
          className="h-full w-full cursor-crosshair"
          role="img"
          aria-label={`Map of ${located.length} incident locations shared by senders`}
        />
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
