interface StaticNetworkProps {
  className?: string;
}

/**
 * The hero's non-WebGL composition.
 *
 * It is not a loading placeholder: on reduced-motion devices, old GPUs and
 * browsers with WebGL disabled this is the finished illustration. The same
 * geometry is drawn by the live scene, so the page has one visual language
 * whether or not a canvas is available.
 */
const PRIMARY_NODES = [
  { x: 86, y: 620, color: '#FF5C35', label: 'SENDER' },
  { x: 284, y: 386, color: '#5CE1DF', label: 'RELAY' },
  { x: 506, y: 536, color: '#5CE1DF', label: 'RELAY' },
  { x: 724, y: 302, color: '#5CE1DF', label: 'RELAY' },
  { x: 982, y: 430, color: '#5CE1DF', label: 'RELAY' },
  { x: 1228, y: 316, color: '#62E6B5', label: 'RECIPIENT' },
] as const;

export function StaticNetwork({ className = '' }: StaticNetworkProps) {
  return (
    <div className={'pointer-events-none absolute inset-0 overflow-hidden ' + className} aria-hidden="true">
      <svg
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
        className="h-full w-full"
        role="presentation"
      >
        <defs>
          <radialGradient id="signal-field" cx="50%" cy="45%" r="60%">
            <stop offset="0%" stopColor="#5CE1DF" stopOpacity="0.105" />
            <stop offset="48%" stopColor="#0B3C3B" stopOpacity="0.15" />
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

        <rect width="1440" height="900" fill="url(#signal-field)" />
        <rect width="1440" height="900" fill="url(#signal-grid)" />

        <g stroke="#8AA39F" strokeOpacity="0.13" strokeWidth="1">
          <line x1="86" y1="620" x2="284" y2="386" />
          <line x1="284" y1="386" x2="506" y2="536" />
          <line x1="506" y1="536" x2="724" y2="302" />
          <line x1="724" y1="302" x2="982" y2="430" />
          <line x1="982" y1="430" x2="1228" y2="316" />
          <line x1="284" y1="386" x2="506" y2="536" />
          <line x1="506" y1="536" x2="724" y2="302" />
          <line x1="724" y1="302" x2="982" y2="430" />
          <line x1="982" y1="430" x2="1228" y2="316" />
          <line x1="284" y1="386" x2="724" y2="302" />
          <line x1="506" y1="536" x2="982" y2="430" />
          <line x1="982" y1="430" x2="1124" y2="624" />
          <line x1="506" y1="536" x2="1124" y2="624" />
          <line x1="724" y1="302" x2="1228" y2="316" />
        </g>

        <g stroke="#5CE1DF" strokeOpacity="0.36" strokeWidth="1.2">
          <line x1="284" y1="386" x2="506" y2="536" />
          <line x1="506" y1="536" x2="724" y2="302" />
          <line x1="724" y1="302" x2="982" y2="430" />
          <line x1="982" y1="430" x2="1228" y2="316" />
        </g>

        <path
          d="M86 620 C184 566 220 432 284 386 S432 472 506 536 S646 348 724 302 S902 380 982 430 S1130 346 1228 316"
          fill="none"
          stroke="url(#signal-route)"
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeDasharray="8 10"
          strokeOpacity="0.9"
          className="animate-route-dash"
        />
        <path
          d="M86 620 C184 566 220 432 284 386 S432 472 506 536 S646 348 724 302 S902 380 982 430 S1130 346 1228 316"
          fill="none"
          stroke="url(#signal-route)"
          strokeWidth="12"
          strokeLinecap="round"
          strokeOpacity="0.08"
          filter="url(#soft-glow)"
        />

        <g>
          <circle cx="86" cy="620" r="92" fill="url(#node-halo-orange)" />
          <circle cx="284" cy="386" r="64" fill="url(#node-halo-cyan)" />
          <circle cx="506" cy="536" r="54" fill="url(#node-halo-cyan)" />
          <circle cx="724" cy="302" r="58" fill="url(#node-halo-cyan)" />
          <circle cx="982" cy="430" r="62" fill="url(#node-halo-cyan)" />
          <circle cx="1228" cy="316" r="88" fill="url(#node-halo-jade)" />
        </g>

        <g>
          {PRIMARY_NODES.map((node) => (
            <g key={`${node.x}-${node.y}`}>
              <circle
                cx={node.x}
                cy={node.y}
                r="22"
                fill="none"
                stroke={node.color}
                strokeOpacity="0.24"
                className="animate-signal-pulse [transform-box:fill-box] [transform-origin:center]"
              />
              <circle
                cx={node.x}
                cy={node.y}
                r="13"
                fill="#07090A"
                stroke={node.color}
                strokeOpacity="0.75"
              />
              <circle cx={node.x} cy={node.y} r="3.4" fill={node.color} />
              <text
                x={node.x}
                y={node.y + 36}
                textAnchor="middle"
                fill="#7E8A83"
                fontSize="8.5"
                fontFamily="Instrument Sans, Inter, sans-serif"
                fontWeight="600"
                letterSpacing="2.2"
              >
                {node.label}
              </text>
            </g>
          ))}
        </g>

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

        <g opacity="0.75">
          <rect x="700" y="648" width="206" height="58" rx="9" fill="#07090A" stroke="#253030" />
          <circle cx="720" cy="668" r="3" fill="#5CE1DF" />
          <text x="732" y="672" fill="#F4F1E8" fontSize="9.5" fontFamily="Instrument Sans, sans-serif" fontWeight="600" letterSpacing="0.6">
            SOS · SIGNED
          </text>
          <text x="732" y="690" fill="#7E8A83" fontSize="8" fontFamily="Instrument Sans, sans-serif">
            hop 2 of 4 · stored locally
          </text>
        </g>
      </svg>
    </div>
  );
}
