import { useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Minus, Plus, RotateCcw, X } from 'lucide-react';

import { StatusChip } from '@/components/primitives/StatusChip';
import { useReducedMotion } from '@/lib/useReducedMotion';
import { linkStatesAt, simTimeAtCursor, type ReplayModel } from '@/lab/replay';
import { buildJourney } from '@/lab/replay';
import { VIEW_HEIGHT, VIEW_WIDTH, layoutNodes, type LayoutNode } from '@/lab/layout';
import { formatMetric } from '@/lab/metrics';
import type { LabEvent, LabReport } from '@/lab/types';

interface NetworkMapProps {
  report: LabReport;
  model: ReplayModel;
  cursor: number;
  selectedMessageId: string | null;
  selectedNodeIndex: number | null;
  onSelectNode: (index: number | null) => void;
}

interface ViewTransform {
  zoom: number;
  x: number;
  y: number;
}

const INITIAL_VIEW: ViewTransform = { zoom: 1, x: 0, y: 0 };
const MIN_ZOOM = 0.7;
const MAX_ZOOM = 4;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/**
 * The recorded network.
 *
 * <p>Every link drawn here is either in the topology the simulator built or
 * was recorded as coming up or going down. No edge is inferred from an event,
 * and no node is invented. The one thing the drawing adds is position: where a
 * node sits on the canvas is a reading aid, not a measurement.
 */
export function NetworkMap({
  report,
  model,
  cursor,
  selectedMessageId,
  selectedNodeIndex,
  onSelectNode,
}: NetworkMapProps) {
  const reduced = useReducedMotion();
  const [view, setView] = useState<ViewTransform>(INITIAL_VIEW);
  const drag = useRef<{ pointerId: number; startX: number; startY: number; originX: number; originY: number } | null>(
    null,
  );

  const currentTime = simTimeAtCursor(model, cursor);
  const links = useMemo(() => linkStatesAt(model, currentTime), [model, currentTime]);
  const layout = useMemo(
    () => layoutNodes(report.simulation.topology, model.nodeCount, model.initialEdges),
    [report.simulation.topology, model.nodeCount, model.initialEdges],
  );
  const positions = useMemo(() => {
    const map = new Map<number, LayoutNode>();
    for (const node of layout) map.set(node.index, node);
    return map;
  }, [layout]);

  const journey = useMemo(
    () =>
      selectedMessageId
        ? buildJourney(model, selectedMessageId, { untilEventIndex: cursor })
        : null,
    [model, selectedMessageId, cursor],
  );

  const journeyRoles = useMemo(() => {
    const roles = new Map<number, 'origin' | 'destination' | 'relay'>();
    if (!journey) return roles;
    if (journey.origin !== null) roles.set(journey.origin, 'origin');
    if (journey.destination !== null) roles.set(journey.destination, 'destination');
    for (const step of journey.steps) {
      if (!roles.has(step.event.nodeIndex)) roles.set(step.event.nodeIndex, 'relay');
    }
    return roles;
  }, [journey]);

  const currentEvent = cursor > 0 ? model.orderedEvents[cursor - 1] : undefined;

  const degreeByNode = useMemo(() => {
    const degree = new Map<number, number>();
    for (const link of links) {
      if (!link.up) continue;
      degree.set(link.from, (degree.get(link.from) ?? 0) + 1);
      degree.set(link.to, (degree.get(link.to) ?? 0) + 1);
    }
    return degree;
  }, [links]);

  const nodeLabel = (index: number): string => {
    const node = report.nodes.find((entry) => entry.index === index);
    return node ? `${node.label} · n${index}` : `n${index}`;
  };

  const zoomBy = (factor: number): void => {
    setView((current) => {
      const zoom = clamp(current.zoom * factor, MIN_ZOOM, MAX_ZOOM);
      const ratio = zoom / current.zoom;
      const centreX = VIEW_WIDTH / 2;
      const centreY = VIEW_HEIGHT / 2;
      return {
        zoom,
        x: centreX - (centreX - current.x) * ratio,
        y: centreY - (centreY - current.y) * ratio,
      };
    });
  };

  const onKeyDown = (event: KeyboardEvent<SVGSVGElement>): void => {
    const step = 24;
    switch (event.key) {
      case 'ArrowLeft':
        event.preventDefault();
        setView((current) => ({ ...current, x: current.x - step }));
        break;
      case 'ArrowRight':
        event.preventDefault();
        setView((current) => ({ ...current, x: current.x + step }));
        break;
      case 'ArrowUp':
        event.preventDefault();
        setView((current) => ({ ...current, y: current.y - step }));
        break;
      case 'ArrowDown':
        event.preventDefault();
        setView((current) => ({ ...current, y: current.y + step }));
        break;
      case '+':
      case '=':
        event.preventDefault();
        zoomBy(1.2);
        break;
      case '-':
      case '_':
        event.preventDefault();
        zoomBy(1 / 1.2);
        break;
      case '0':
        event.preventDefault();
        setView(INITIAL_VIEW);
        break;
      case 'Escape':
        onSelectNode(null);
        break;
      default:
        break;
    }
  };

  const downLinks = links.filter((link) => !link.up);
  const isolated = layout.filter((node) => (degreeByNode.get(node.index) ?? 0) === 0);

  return (
    <figure className="surface overflow-hidden" aria-labelledby="lab-map-title">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-base-700 px-5 py-4">
        <div className="flex flex-wrap items-center gap-2.5">
          <StatusChip tone="pending" pulse={!reduced}>
            Simulated
          </StatusChip>
          <h2 id="lab-map-title" className="text-sm font-medium text-ink-primary">
            {report.simulation.topology.toLowerCase().replace(/_/g, ' ')} ·{' '}
            {model.nodeCount} nodes · {model.initialEdges.length} recorded links
          </h2>
        </div>
        <div className="flex items-center gap-1.5">
          <IconButton label="Zoom in" onClick={() => zoomBy(1.2)}>
            <Plus size={14} aria-hidden="true" />
          </IconButton>
          <IconButton label="Zoom out" onClick={() => zoomBy(1 / 1.2)}>
            <Minus size={14} aria-hidden="true" />
          </IconButton>
          <IconButton label="Reset the view" onClick={() => setView(INITIAL_VIEW)}>
            <RotateCcw size={14} aria-hidden="true" />
          </IconButton>
        </div>
      </header>

      <div className="relative bg-base-900">
        <div className="pointer-events-none mx-4 mt-3 rounded-lg border border-pending/40 bg-base-950/85 px-3 py-1.5 sm:absolute sm:left-4 sm:top-4 sm:m-0">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-pending">
            Simulated — not real radio telemetry
          </p>
        </div>
        <svg
          viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
          className="block h-auto w-full cursor-grab touch-none active:cursor-grabbing"
          role="img"
          tabIndex={0}
          aria-label={`Recorded ${report.simulation.topology} topology with ${model.nodeCount} nodes and ${links.length} links at simulated time ${Math.round((currentTime - model.startMillis) / 1000)} seconds. Use the arrow keys to pan, plus and minus to zoom, and the node list below the map to inspect a node.`}
          onKeyDown={onKeyDown}
          onPointerDown={(event) => {
            if (event.button !== 0) return;
            event.currentTarget.setPointerCapture(event.pointerId);
            drag.current = {
              pointerId: event.pointerId,
              startX: event.clientX,
              startY: event.clientY,
              originX: view.x,
              originY: view.y,
            };
          }}
          onPointerMove={(event) => {
            const current = drag.current;
            if (!current || current.pointerId !== event.pointerId) return;
            setView((previous) => ({
              ...previous,
              x: current.originX + (event.clientX - current.startX),
              y: current.originY + (event.clientY - current.startY),
            }));
          }}
          onPointerUp={(event) => {
            if (drag.current?.pointerId === event.pointerId) drag.current = null;
            if (event.currentTarget.hasPointerCapture(event.pointerId)) {
              event.currentTarget.releasePointerCapture(event.pointerId);
            }
          }}
          onPointerCancel={() => {
            drag.current = null;
          }}
        >
          <defs>
            <marker
              id="lab-arrow-forwarded"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="5"
              markerHeight="5"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill="rgba(92, 225, 223, 0.55)" />
            </marker>
            <marker
              id="lab-arrow-accepted"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="5"
              markerHeight="5"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#62E6B5" />
            </marker>
          </defs>

          <g transform={`translate(${view.x} ${view.y}) scale(${view.zoom})`}>
            {links.map((link) => {
              const from = positions.get(link.from);
              const to = positions.get(link.to);
              if (!from || !to) return null;
              const isDown = !link.up;
              const recentlyChanged =
                link.changedAt > model.startMillis && currentTime - link.changedAt <= 500;
              return (
                <line
                  key={link.key}
                  x1={from.x}
                  y1={from.y}
                  x2={to.x}
                  y2={to.y}
                  stroke={
                    isDown
                      ? 'rgba(255, 92, 53, 0.55)'
                      : recentlyChanged
                        ? 'rgba(92, 225, 223, 0.75)'
                        : 'rgba(92, 225, 223, 0.22)'
                  }
                  strokeWidth={recentlyChanged || isDown ? 2.5 : 1.5}
                  strokeDasharray={isDown ? '7 7' : undefined}
                />
              );
            })}

            {isDownMarker(links, positions)}

            {journey && journey.handoffs.length > 0
              ? journey.handoffs
                  .filter((handoff) => handoff.atEpochMillis <= currentTime)
                  .map((handoff, index) => {
                    const from = positions.get(handoff.from);
                    const to = positions.get(handoff.to);
                    if (!from || !to) return null;
                    const confirmed = handoff.kind === 'accepted';
                    return (
                      <line
                        key={`handoff-${index}-${handoff.from}-${handoff.to}-${handoff.atEpochMillis}`}
                        x1={from.x}
                        y1={from.y}
                        x2={to.x}
                        y2={to.y}
                        stroke={confirmed ? 'rgba(98, 230, 181, 0.85)' : 'rgba(92, 225, 223, 0.55)'}
                        strokeWidth={confirmed ? 3 : 2}
                        strokeDasharray={confirmed ? undefined : '4 5'}
                        markerEnd={
                          confirmed ? 'url(#lab-arrow-accepted)' : 'url(#lab-arrow-forwarded)'
                        }
                      />
                    );
                  })
              : null}

            {layout.map((node) => {
              const degree = degreeByNode.get(node.index) ?? 0;
              const isSelected = selectedNodeIndex === node.index;
              const role = journeyRoles.get(node.index);
              const isCurrent = currentEvent?.nodeIndex === node.index;
              return (
                <g
                  key={node.index}
                  className="cursor-pointer outline-none"
                  onClick={() => onSelectNode(isSelected ? null : node.index)}
                >
                  <title>
                    {nodeLabel(node.index)} — {degree === 0 ? 'isolated' : `${degree} active links`}
                    {role ? ` — ${role} in the selected message` : ''}
                  </title>
                  {isCurrent && !reduced ? (
                    <circle cx={node.x} cy={node.y} r={34} className="lab-halo" fill="rgba(92,225,223,0.18)" />
                  ) : null}
                  {role ? (
                    <circle
                      cx={node.x}
                      cy={node.y}
                      r={30}
                      fill="none"
                      stroke={
                        role === 'origin'
                          ? 'rgba(255, 92, 53, 0.9)'
                          : role === 'destination'
                            ? 'rgba(98, 230, 181, 0.9)'
                            : 'rgba(92, 225, 223, 0.6)'
                      }
                      strokeWidth={2}
                      strokeDasharray={role === 'relay' ? '3 4' : undefined}
                    />
                  ) : null}
                  <circle
                    cx={node.x}
                    cy={node.y}
                    r={24}
                    fill={degree === 0 ? '#1a2223' : '#0e1213'}
                    stroke={
                      isSelected
                        ? '#F4F1E8'
                        : degree === 0
                          ? 'rgba(255, 92, 53, 0.75)'
                          : 'rgba(92, 225, 223, 0.7)'
                    }
                    strokeWidth={isSelected ? 3 : 2}
                    strokeDasharray={degree === 0 ? '5 5' : undefined}
                  />
                  <text
                    x={node.x}
                    y={node.y + 4.5}
                    textAnchor="middle"
                    fontSize="16"
                    fontWeight="600"
                    fill={degree === 0 ? '#A8B1AA' : '#F4F1E8'}
                    style={{ pointerEvents: 'none', fontFamily: 'ui-monospace, monospace' }}
                  >
                    {node.index}
                  </text>
                  <text
                    x={node.x}
                    y={node.y + 44}
                    textAnchor="middle"
                    fontSize="12"
                    fill={isSelected ? '#F4F1E8' : '#7E8A83'}
                    style={{ pointerEvents: 'none' }}
                  >
                    {degree === 0 ? 'isolated' : `${degree} link${degree === 1 ? '' : 's'}`}
                  </text>
                </g>
              );
            })}
          </g>
        </svg>

        <div className="pointer-events-none mx-4 mb-4 mt-3 rounded-lg bg-base-950/80 px-3 py-2 text-[10px] leading-relaxed text-ink-tertiary sm:absolute sm:bottom-4 sm:left-4 sm:m-0">
          <span className="mr-3 inline-flex items-center gap-1.5">
            <span className="inline-block h-px w-5 bg-network" aria-hidden="true" /> recorded link
          </span>
          <span className="mr-3 inline-flex items-center gap-1.5">
            <span className="inline-block h-px w-5 border-t border-dashed border-emergency" aria-hidden="true" />{' '}
            link down at this point
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block h-px w-5 border-t-2 border-delivered" aria-hidden="true" />{' '}
            selected message handoff
          </span>
        </div>
      </div>

      <figcaption className="border-t border-base-700 px-5 py-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-[11px] leading-relaxed text-ink-tertiary">
            Positions are a reading aid. Links and link states come from the report;{' '}
            {downLinks.length} {downLinks.length === 1 ? 'link is' : 'links are'} down at this point
            {isolated.length > 0
              ? `, and ${isolated.length} ${isolated.length === 1 ? 'node is' : 'nodes are'} isolated`
              : ''}
            .
          </p>
          <span className="tabular text-[11px] text-ink-tertiary">
            t+{((currentTime - model.startMillis) / 1000).toFixed(2)} s simulated
          </span>
        </div>

        <div className="mt-4">
          <h3 className="eyebrow">Nodes</h3>
          <ul className="mt-2 flex flex-wrap gap-1.5">
            {layout.map((node) => {
              const degree = degreeByNode.get(node.index) ?? 0;
              const selected = selectedNodeIndex === node.index;
              const role = journeyRoles.get(node.index);
              return (
                <li key={node.index}>
                  <button
                    type="button"
                    onClick={() => onSelectNode(selected ? null : node.index)}
                    aria-pressed={selected}
                    className={
                      'inline-flex min-h-[30px] items-center gap-1.5 rounded-full border px-2.5 text-[11px] transition-colors ' +
                      (selected
                        ? 'border-ink-primary/40 bg-base-700 text-ink-primary'
                        : 'border-base-600 text-ink-secondary hover:border-network/40 hover:text-ink-primary')
                    }
                  >
                    <span className="tabular font-mono">n{node.index}</span>
                    <span className="text-ink-tertiary">
                      {degree === 0 ? 'isolated' : `${degree} link${degree === 1 ? '' : 's'}`}
                    </span>
                    {role ? (
                      <span className="text-[9px] uppercase tracking-widest text-network">{role}</span>
                    ) : null}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>

        {selectedNodeIndex !== null ? (
          <NodeDetails
            index={selectedNodeIndex}
            report={report}
            activeLinks={degreeByNode.get(selectedNodeIndex) ?? 0}
            recordedEvents={(model.eventsByNode.get(selectedNodeIndex) ?? []).filter(
              (event) => (model.eventOrder.get(event) ?? -1) < cursor,
            )}
            onClose={() => onSelectNode(null)}
          />
        ) : null}
      </figcaption>
    </figure>
  );
}

/** Draws a small cross at the midpoint of every link that is down. */
function isDownMarker(
  links: ReturnType<typeof linkStatesAt>,
  positions: Map<number, LayoutNode>,
) {
  return links
    .filter((link) => !link.up)
    .map((link) => {
      const from = positions.get(link.from);
      const to = positions.get(link.to);
      if (!from || !to) return null;
      const x = (from.x + to.x) / 2;
      const y = (from.y + to.y) / 2;
      return (
        <g key={`down-${link.key}`} aria-hidden="true">
          <circle cx={x} cy={y} r={10} fill="#040607" stroke="rgba(255, 92, 53, 0.7)" strokeWidth={1.5} />
          <path
            d={`M ${x - 4} ${y - 4} L ${x + 4} ${y + 4} M ${x + 4} ${y - 4} L ${x - 4} ${y + 4}`}
            stroke="#FF5C35"
            strokeWidth={1.8}
            strokeLinecap="round"
          />
        </g>
      );
    });
}

function NodeDetails({
  index,
  report,
  activeLinks,
  recordedEvents,
  onClose,
}: {
  index: number;
  report: LabReport;
  activeLinks: number;
  recordedEvents: readonly LabEvent[];
  onClose: () => void;
}) {
  const node = report.nodes.find((entry) => entry.index === index);
  const upToCursor = recordedEvents;
  const lastEvent = upToCursor[upToCursor.length - 1];

  return (
    <div className="mt-4 rounded-xl border border-base-600 bg-base-900/60 px-3.5 py-3">
      <div className="flex items-center justify-between gap-3">
        <h4 className="text-[12px] font-medium text-ink-primary">
          {node?.label ?? `node ${index}`}
          <span className="ml-2 font-mono text-[10px] text-ink-tertiary">n{index}</span>
        </h4>
        <button
          type="button"
          onClick={onClose}
          aria-label="Clear the selected node"
          className="grid h-6 w-6 place-items-center rounded text-ink-tertiary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
        >
          <X size={12} aria-hidden="true" />
        </button>
      </div>

      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1.5 text-[10px] sm:grid-cols-3">
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Links now</dt>
          <dd className="tabular text-ink-secondary">{activeLinks}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Links at end</dt>
          <dd className="tabular text-ink-secondary">{formatMetric(node?.degreeAtEnd ?? null, 'count')}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Events seen</dt>
          <dd className="tabular text-ink-secondary">{upToCursor.length}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Composed</dt>
          <dd className="tabular text-ink-secondary">{formatMetric(node?.originated ?? null, 'count')}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Accepted</dt>
          <dd className="tabular text-ink-secondary">{formatMetric(node?.accepted ?? null, 'count')}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Forwarded</dt>
          <dd className="tabular text-ink-secondary">{formatMetric(node?.forwarded ?? null, 'count')}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Delivered</dt>
          <dd className="tabular text-ink-secondary">{formatMetric(node?.delivered ?? null, 'count')}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Rejected</dt>
          <dd className="tabular text-ink-secondary">{formatMetric(node?.rejected ?? null, 'count')}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-ink-tertiary">Still queued</dt>
          <dd className="tabular text-ink-secondary">{formatMetric(node?.stillQueued ?? null, 'count')}</dd>
        </div>
      </dl>

      <p className="mt-2 border-t border-base-700 pt-2 text-[10px] leading-relaxed text-ink-tertiary">
        Counters cover the whole run and come from the report&rsquo;s node table. The link count is
        at the replay cursor.
        {lastEvent ? (
          <>
            {' '}Latest event at this point: {lastEvent.kind} (round {lastEvent.round}).
          </>
        ) : (
          ' No event from this node has happened yet.'
        )}
      </p>
    </div>
  );
}

function IconButton({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-base-600 text-ink-secondary transition-colors hover:border-network/40 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
    >
      {children}
    </button>
  );
}
