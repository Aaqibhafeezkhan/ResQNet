import {
  HERO_LINKS,
  HERO_NODES,
  heroLinkId,
  type HeroNodeId,
  type HeroState,
} from './heroScene';

interface StaticNetworkProps {
  state: HeroState;
  className?: string;
}

/**
 * Projection from world units to the SVG viewBox.
 *
 * The WebGL scene and this illustration share one set of world coordinates, so
 * a device sits in the same place in both. The fallback is not a placeholder:
 * on a device with no WebGL, or with reduced motion requested, this is the
 * finished illustration of the same story.
 */
const VIEW_WIDTH = 1000;
const VIEW_HEIGHT = 560;
const SCALE = 82;
const ORIGIN_X = VIEW_WIDTH / 2;
const ORIGIN_Y = VIEW_HEIGHT / 2;

function project(position: readonly [number, number, number]): [number, number] {
  const [x, y] = position;
  return [ORIGIN_X + x * SCALE, ORIGIN_Y - y * SCALE];
}

const NODE_ROLE_LABEL: Record<HeroNodeId, string> = {
  amina: 'SENDER',
  jonas: 'RELAY',
  mei: 'RELAY',
  tomas: 'RELAY',
  sana: 'RELAY',
  post: 'RECIPIENT',
};

export function StaticNetwork({ state, className = '' }: StaticNetworkProps) {
  const broken = new Set(state.brokenLinks);
  const routePairs = new Set<string>();
  for (let index = 0; index < state.route.length - 1; index += 1) {
    const from = state.route[index];
    const to = state.route[index + 1];
    if (from && to) routePairs.add(heroLinkId(from, to));
  }

  const packet = packetPosition(state);
  const ack = ackPosition(state);

  return (
    <div
      className={'pointer-events-none absolute inset-0 overflow-hidden ' + className}
      aria-hidden="true"
    >
      <svg
        viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
        preserveAspectRatio="xMidYMid slice"
        className="h-full w-full"
        role="presentation"
      >
        <defs>
          <radialGradient id="signal-field" cx="50%" cy="45%" r="62%">
            <stop offset="0%" stopColor="#5CE1DF" stopOpacity="0.12" />
            <stop offset="46%" stopColor="#0B3C3B" stopOpacity="0.16" />
            <stop offset="100%" stopColor="#040607" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="node-halo-cyan" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#5CE1DF" stopOpacity="0.6" />
            <stop offset="38%" stopColor="#5CE1DF" stopOpacity="0.18" />
            <stop offset="100%" stopColor="#5CE1DF" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="node-halo-orange" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#FF5C35" stopOpacity="0.72" />
            <stop offset="40%" stopColor="#FF5C35" stopOpacity="0.2" />
            <stop offset="100%" stopColor="#FF5C35" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="node-halo-jade" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#62E6B5" stopOpacity="0.62" />
            <stop offset="40%" stopColor="#62E6B5" stopOpacity="0.17" />
            <stop offset="100%" stopColor="#62E6B5" stopOpacity="0" />
          </radialGradient>
          <linearGradient id="signal-route" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#FF5C35" />
            <stop offset="38%" stopColor="#5CE1DF" />
            <stop offset="100%" stopColor="#62E6B5" />
          </linearGradient>
          <pattern id="signal-grid" width="96" height="96" patternUnits="userSpaceOnUse">
            <path d="M 96 0 L 0 0 0 96" fill="none" stroke="#F4F1E8" strokeOpacity="0.028" />
          </pattern>
          <filter id="soft-glow" x="-80%" y="-80%" width="260%" height="260%">
            <feGaussianBlur stdDeviation="9" />
          </filter>
        </defs>

        <rect width={VIEW_WIDTH} height={VIEW_HEIGHT} fill="url(#signal-field)" />
        <rect width={VIEW_WIDTH} height={VIEW_HEIGHT} fill="url(#signal-grid)" />

        <g stroke="#8AA39F" strokeOpacity="0.13" strokeWidth="1">
          {HERO_LINKS.map((link) => {
            const from = node(link.from);
            const to = node(link.to);
            if (!from || !to) return null;
            const [x1, y1] = project(from.position);
            const [x2, y2] = project(to.position);
            return (
              <line
                key={link.id}
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                opacity={broken.has(link.id) ? 0.18 : 1}
                strokeDasharray={broken.has(link.id) ? '3 7' : undefined}
              />
            );
          })}
        </g>

        <g stroke="#5CE1DF" strokeOpacity="0.4" strokeWidth="1.4">
          {HERO_LINKS.map((link) => {
            if (!routePairs.has(link.id) || broken.has(link.id)) return null;
            const from = node(link.from);
            const to = node(link.to);
            if (!from || !to) return null;
            const [x1, y1] = project(from.position);
            const [x2, y2] = project(to.position);
            return <line key={link.id} x1={x1} y1={y1} x2={x2} y2={y2} />;
          })}
        </g>

        {broken.size > 0 ? (
          <g stroke="#FF5C35" strokeOpacity="0.55" strokeWidth="1.2" strokeDasharray="4 6">
            {HERO_LINKS.map((link) => {
              if (!broken.has(link.id)) return null;
              const from = node(link.from);
              const to = node(link.to);
              if (!from || !to) return null;
              const [x1, y1] = project(from.position);
              const [x2, y2] = project(to.position);
              return <line key={link.id} x1={x1} y1={y1} x2={x2} y2={y2} />;
            })}
          </g>
        ) : null}

        <g>
          {HERO_NODES.map((spec) => {
            const [cx, cy] = project(spec.position);
            const offline = state.offline.includes(spec.id);
            const halo =
              spec.role === 'sender'
                ? 'url(#node-halo-orange)'
                : spec.role === 'recipient'
                  ? 'url(#node-halo-jade)'
                  : 'url(#node-halo-cyan)';
            const color =
              spec.role === 'sender'
                ? '#FF5C35'
                : spec.role === 'recipient'
                  ? '#62E6B5'
                  : '#5CE1DF';
            return (
              <g key={spec.id} opacity={offline ? 0.3 : 1}>
                <circle cx={cx} cy={cy} r={44 * spec.scale} fill={halo} />
                <circle
                  cx={cx}
                  cy={cy}
                  r="22"
                  fill="none"
                  stroke={color}
                  strokeOpacity="0.24"
                  className="animate-signal-pulse [transform-box:fill-box] [transform-origin:center]"
                />
                <circle cx={cx} cy={cy} r="13" fill="#07090A" stroke={color} strokeOpacity="0.75" />
                <circle cx={cx} cy={cy} r="3.4" fill={color} />
                <text
                  x={cx}
                  y={cy + 36}
                  textAnchor="middle"
                  fill="#7E8A83"
                  fontSize="8.5"
                  fontFamily="Instrument Sans, Inter, sans-serif"
                  fontWeight="600"
                  letterSpacing="2.2"
                >
                  {NODE_ROLE_LABEL[spec.id]}
                </text>
              </g>
            );
          })}
        </g>

        {packet ? (
          <g>
            <circle cx={packet[0]} cy={packet[1]} r="16" fill="#FF5C35" opacity="0.22" />
            <circle cx={packet[0]} cy={packet[1]} r="5" fill="#FFB090" />
            <circle cx={packet[0]} cy={packet[1]} r="5" fill="none" stroke="#FFB090" strokeWidth="1">
              <animate attributeName="r" values="5;18;5" dur="1.8s" repeatCount="indefinite" />
              <animate attributeName="opacity" values="0.9;0;0.9" dur="1.8s" repeatCount="indefinite" />
            </circle>
          </g>
        ) : null}

        {ack ? (
          <g>
            <circle cx={ack[0]} cy={ack[1]} r="12" fill="#62E6B5" opacity="0.24" />
            <circle cx={ack[0]} cy={ack[1]} r="4" fill="#9DF2D1" />
          </g>
        ) : null}

        <g fill="#5CE1DF">
          <circle cx="380" cy="244" r="1.6" opacity="0.5" />
          <circle cx="892" cy="196" r="1.3" opacity="0.4" />
          <circle cx="1114" cy="520" r="1.7" opacity="0.5" />
          <circle cx="636" cy="672" r="1.2" opacity="0.35" />
          <circle cx="200" cy="240" r="1.1" opacity="0.3" />
          <circle cx="1326" cy="576" r="1.4" opacity="0.3" />
          <circle cx="430" cy="706" r="1.1" opacity="0.28" />
          <circle cx="1042" cy="244" r="1.3" opacity="0.35" />
          <circle cx="782" cy="620" r="1.2" opacity="0.32" />
          <circle cx="1288" cy="158" r="1.5" opacity="0.28" />
          <circle cx="130" cy="400" r="1.2" opacity="0.3" />
          <circle cx="934" cy="690" r="1.5" opacity="0.26" />
        </g>
      </svg>
    </div>
  );
}

function node(id: HeroNodeId) {
  return HERO_NODES.find((spec) => spec.id === id);
}

function packetPosition(state: HeroState): [number, number] | null {
  const journey = state.journey;
  if (journey.length < 2 || state.packetProgress <= 0) return null;
  const legs = journey.length - 1;
  const clamped = Math.min(state.packetProgress, legs);
  const index = Math.min(legs - 1, Math.floor(clamped));
  const t = clamped - index;
  const from = node(journey[index] ?? 'amina');
  const to = node(journey[index + 1] ?? 'post');
  if (!from || !to) return null;
  const [x1, y1] = project(from.position);
  const [x2, y2] = project(to.position);
  return [x1 + (x2 - x1) * t, y1 + (y2 - y1) * t - Math.sin(t * Math.PI) * 30];
}

function ackPosition(state: HeroState): [number, number] | null {
  const journey = state.journey;
  if (journey.length < 2 || state.ackProgress <= 0) return null;
  const from = node(journey[journey.length - 1] ?? 'post');
  const to = node(journey[0] ?? 'amina');
  if (!from || !to) return null;
  const [x1, y1] = project(from.position);
  const [x2, y2] = project(to.position);
  const t = 1 - state.ackProgress;
  return [x1 + (x2 - x1) * t, y1 + (y2 - y1) * t - Math.sin(t * Math.PI) * 30];
}
