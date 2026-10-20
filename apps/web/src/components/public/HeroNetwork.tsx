import { RadioTower } from 'lucide-react';

const NODES = [
  { id: 'A', x: 86, y: 300, active: true, kind: 'origin' },
  { id: 'J', x: 252, y: 176, active: true, kind: 'relay' },
  { id: 'M', x: 236, y: 382, active: false, kind: 'relay' },
  { id: 'T', x: 416, y: 246, active: true, kind: 'relay' },
  { id: 'S', x: 398, y: 394, active: false, kind: 'relay' },
  { id: 'FP', x: 556, y: 168, active: true, kind: 'destination' },
] as const;

const LINKS = [
  { from: [86, 300], to: [252, 176], active: true },
  { from: [86, 300], to: [236, 382], active: false },
  { from: [252, 176], to: [416, 246], active: true },
  { from: [236, 382], to: [416, 246], active: false },
  { from: [236, 382], to: [398, 394], active: false },
  { from: [416, 246], to: [556, 168], active: true },
  { from: [416, 246], to: [398, 394], active: false },
  { from: [398, 394], to: [556, 168], active: false },
] as const;

export function HeroNetwork() {
  return (
    <figure className="relative">
      <div className="relative overflow-hidden rounded-[28px] border border-base-600 bg-base-900/80 p-3 shadow-lift sm:p-4">
        <div className="grid-field absolute inset-0 opacity-50" aria-hidden="true" />
        <div
          className="absolute inset-0 bg-[radial-gradient(70%_70%_at_28%_25%,rgba(67,216,220,0.1),transparent_64%)]"
          aria-hidden="true"
        />

        <div className="relative flex items-center justify-between px-2 pt-1">
          <span className="eyebrow inline-flex items-center gap-2">
            <span className="h-1.5 w-1.5 rounded-full bg-network" aria-hidden="true" />
            Simulated route
          </span>
          <span className="rounded-full border border-base-600 bg-base-850/80 px-2.5 py-1 text-[9px] font-semibold uppercase tracking-widest text-ink-tertiary">
            No internet
          </span>
        </div>

        <svg
          viewBox="0 0 640 480"
          className="relative mt-1 w-full"
          role="img"
          aria-label="Simulated mesh route: Amina's phone relays an SOS through Jonas and Tomas to a field post."
        >
          <defs>
            <radialGradient id="hero-glow" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#43d8dc" stopOpacity="0.18" />
              <stop offset="100%" stopColor="#43d8dc" stopOpacity="0" />
            </radialGradient>
            <linearGradient id="route-line" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#e74b4b" />
              <stop offset="50%" stopColor="#43d8dc" />
              <stop offset="100%" stopColor="#45d19a" />
            </linearGradient>
          </defs>

          <circle cx="320" cy="250" r="250" fill="url(#hero-glow)" />

          {LINKS.map((link, index) => (
            <line
              key={`${link.from.join('-')}-${link.to.join('-')}-${index}`}
              x1={link.from[0]}
              y1={link.from[1]}
              x2={link.to[0]}
              y2={link.to[1]}
              stroke={link.active ? '#43d8dc' : '#3b474c'}
              strokeWidth={link.active ? 1.4 : 1}
              strokeOpacity={link.active ? 0.8 : 0.38}
              strokeDasharray={link.active ? '5 6' : '2 5'}
              className={link.active ? 'animate-route-dash' : undefined}
            />
          ))}

          <path
            d="M86 300 C150 260 188 205 252 176 S350 220 416 246 S506 192 556 168"
            fill="none"
            stroke="url(#route-line)"
            strokeWidth="2"
            strokeLinecap="round"
            strokeDasharray="7 8"
            strokeOpacity="0.86"
            className="animate-route-dash"
          />

          {NODES.map((node) => {
            const fill =
              node.kind === 'origin'
                ? '#e74b4b'
                : node.kind === 'destination'
                  ? '#45d19a'
                  : node.active
                    ? '#43d8dc'
                    : '#818c86';
            return (
              <g key={node.id}>
                {node.active ? (
                  <circle
                    cx={node.x}
                    cy={node.y}
                    r="22"
                    fill={fill}
                    opacity="0.1"
                    className="animate-signal-pulse [transform-box:fill-box] [transform-origin:center]"
                  />
                ) : null}
                <circle
                  cx={node.x}
                  cy={node.y}
                  r="15"
                  fill="#0c1012"
                  stroke={fill}
                  strokeWidth="1.4"
                  strokeOpacity={node.active ? 0.95 : 0.55}
                />
                <circle cx={node.x} cy={node.y} r="3" fill={fill} opacity={node.active ? 1 : 0.55} />
                <text
                  x={node.x}
                  y={node.y + 34}
                  textAnchor="middle"
                  fill={node.active ? '#f2f0e9' : '#818c86'}
                  fontSize="10.5"
                  fontFamily="Inter, system-ui, sans-serif"
                  fontWeight="600"
                >
                  {node.id === 'FP' ? 'FIELD POST' : `PHONE ${node.id}`}
                </text>
              </g>
            );
          })}

          <g transform="translate(252 96)">
            <rect width="138" height="42" rx="9" fill="#0c1012" stroke="#273136" />
            <circle cx="14" cy="14" r="3" fill="#43d8dc" />
            <text x="24" y="17" fill="#f2f0e9" fontSize="9.5" fontFamily="Inter, sans-serif" fontWeight="600">
              SOS · AMINA
            </text>
            <text x="24" y="30" fill="#818c86" fontSize="8.5" fontFamily="Inter, sans-serif">
              signed · queued locally
            </text>
          </g>

          <g transform="translate(472 320)">
            <rect width="116" height="42" rx="9" fill="#0c1012" stroke="#273136" />
            <circle cx="14" cy="14" r="3" fill="#45d19a" />
            <text x="24" y="17" fill="#f2f0e9" fontSize="9.5" fontFamily="Inter, sans-serif" fontWeight="600">
              3 HOPS
            </text>
            <text x="24" y="30" fill="#818c86" fontSize="8.5" fontFamily="Inter, sans-serif">
              acknowledgement
            </text>
          </g>
        </svg>

        <div className="relative flex flex-wrap items-center justify-between gap-2 px-2 pb-1 pt-2 text-[9px] uppercase tracking-widest text-ink-tertiary">
          <span>Amina → Jonas → Tomas → Field post</span>
          <span className="inline-flex items-center gap-1.5">
            <RadioTower size={11} aria-hidden="true" />
            Concept, not device footage
          </span>
        </div>
      </div>
      <figcaption className="mt-3 px-1 text-[11px] leading-relaxed text-ink-tertiary">
        A modelled delivery path, not live radio telemetry. Try the interactive
        version to cut links and watch the route change.
      </figcaption>
    </figure>
  );
}
