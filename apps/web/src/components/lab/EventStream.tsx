import { Search, SlidersHorizontal, X } from 'lucide-react';
import { useMemo } from 'react';

import { useLabStore } from '@/lab/labStore';
import type { LabEvent } from '@/lab/types';

const KIND_TONE: Record<string, string> = {
  accepted: 'bg-delivered',
  delivered: 'bg-delivered',
  acknowledged: 'bg-network',
  'acknowledgement-sent': 'bg-network',
  forwarded: 'bg-network',
  'peer-connected': 'bg-pending',
  'peer-disconnected': 'bg-pending',
  state: 'bg-pending',
  triage: 'bg-ink-tertiary',
  backpressure: 'bg-pending',
  rejected: 'bg-emergency',
};

function toneFor(kind: string): string {
  return KIND_TONE[kind] ?? 'bg-base-400';
}

function matches(event: LabEvent, query: string): boolean {
  if (query === '') return true;
  const haystack = [
    event.kind,
    event.node,
    event.messageId ?? '',
    event.detail ?? '',
    event.reason ?? '',
  ]
    .join(' ')
    .toLowerCase();
  return haystack.includes(query);
}

/**
 * The recorded event stream, filtered and read backwards from the cursor.
 *
 * <p>The list shows what had happened at the cursor, not the whole report.
 * Filtering hides rows; it never changes the cursor or the metrics, so a
 * filtered view cannot be mistaken for a different run.
 */
export function EventStream() {
  const model = useLabStore((state) => state.model);
  const cursor = useLabStore((state) => state.cursor);
  const activeKinds = useLabStore((state) => state.activeKinds);
  const search = useLabStore((state) => state.search);
  const toggleKind = useLabStore((state) => state.toggleKind);
  const setKinds = useLabStore((state) => state.setKinds);
  const setSearch = useLabStore((state) => state.setSearch);
  const selectMessage = useLabStore((state) => state.selectMessage);
  const selectedMessageId = useLabStore((state) => state.selectedMessageId);

  const counts = useMemo(() => {
    const map = new Map<string, number>();
    if (!model) return map;
    for (const event of model.orderedEvents.slice(0, cursor)) {
      map.set(event.kind, (map.get(event.kind) ?? 0) + 1);
    }
    return map;
  }, [model, cursor]);

  const visible = useMemo(() => {
    if (!model) return [];
    const query = search.trim().toLowerCase();
    const filtered: LabEvent[] = [];
    for (let index = 0; index < cursor; index += 1) {
      const event = model.orderedEvents[index];
      if (!event) continue;
      if (!activeKinds.has(event.kind)) continue;
      if (!matches(event, query)) continue;
      filtered.push(event);
    }
    // Newest first, capped so a 200 000-event report cannot freeze the list.
    return filtered.slice(-120).reverse();
  }, [model, cursor, activeKinds, search]);

  if (!model) return null;

  const kinds = [...new Set(model.orderedEvents.map((event) => event.kind))].sort();
  const allActive = kinds.every((kind) => activeKinds.has(kind));

  return (
    <section className="surface flex flex-col" aria-labelledby="event-stream-title">
      <header className="border-b border-base-700 px-5 py-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="event-stream-title" className="text-sm font-medium text-ink-primary">
            Event stream
          </h2>
          <span className="tabular text-[10px] text-ink-tertiary">
            {visible.length} shown · {cursor} recorded
          </span>
        </div>

        <div className="relative mt-3">
          <Search
            size={13}
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-tertiary"
            aria-hidden="true"
          />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search message, node or detail"
            aria-label="Search recorded events"
            className="w-full rounded-lg border border-base-600 bg-base-900 py-1.5 pl-8 pr-8 text-[12px] text-ink-primary placeholder:text-ink-tertiary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
          />
          {search ? (
            <button
              type="button"
              onClick={() => setSearch('')}
              aria-label="Clear the search"
              className="absolute right-1 top-1/2 grid h-6 w-6 -translate-y-1/2 place-items-center rounded text-ink-tertiary hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              <X size={12} aria-hidden="true" />
            </button>
          ) : null}
        </div>

        <div className="mt-3 flex items-start gap-2">
          <SlidersHorizontal
            size={12}
            className="mt-1.5 shrink-0 text-ink-tertiary"
            aria-hidden="true"
          />
          <div className="flex flex-wrap gap-1">
            {kinds.map((kind) => {
              const active = activeKinds.has(kind);
              return (
                <button
                  key={kind}
                  type="button"
                  onClick={() => toggleKind(kind)}
                  aria-pressed={active}
                  title={`${counts.get(kind) ?? 0} recorded by this point`}
                  className={
                    'inline-flex min-h-[24px] items-center gap-1.5 rounded-full border px-2 text-[10px] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network ' +
                    (active
                      ? 'border-base-500 bg-base-800 text-ink-secondary'
                      : 'border-base-700 text-ink-tertiary opacity-50 hover:opacity-80')
                  }
                >
                  <span className={`h-1.5 w-1.5 rounded-full ${toneFor(kind)}`} aria-hidden="true" />
                  {kind}
                  <span className="tabular text-ink-tertiary">{counts.get(kind) ?? 0}</span>
                </button>
              );
            })}
            <button
              type="button"
              onClick={() => setKinds(allActive ? [] : kinds)}
              className="inline-flex min-h-[24px] items-center rounded-full px-2 text-[10px] font-medium text-ink-tertiary underline decoration-base-600 underline-offset-4 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              {allActive ? 'Hide all' : 'Show all'}
            </button>
          </div>
        </div>
      </header>

      <ol className="max-h-[440px] divide-y divide-base-800 overflow-y-auto">
        {visible.length === 0 ? (
          <li className="px-5 py-8 text-center text-[12px] text-ink-tertiary">
            {cursor === 0
              ? 'No events have happened yet. Press play or scrub the timeline.'
              : 'No event matches these filters at this point in the replay.'}
          </li>
        ) : (
          visible.map((event, index) => {
            const selected = event.messageId !== null && event.messageId === selectedMessageId;
            return (
              <li
                key={`${event.atEpochMillis}-${event.nodeIndex}-${event.kind}-${event.messageId ?? ''}-${index}`}
                onClick={() => {
                  if (event.messageId) selectMessage(event.messageId);
                }}
                className={
                  'flex items-start gap-3 px-4 py-2.5 text-left transition-colors ' +
                  (event.messageId ? 'cursor-pointer hover:bg-base-800/70 ' : '') +
                  (selected ? 'bg-network/[0.05]' : '')
                }
              >
                <span className="tabular mt-0.5 w-[62px] shrink-0 font-mono text-[10px] text-ink-tertiary">
                  +{((event.atEpochMillis - model.startMillis) / 1000).toFixed(2)}s
                </span>
                <span
                  className={`mt-1 h-1.5 w-1.5 shrink-0 rounded-full ${toneFor(event.kind)}`}
                  aria-hidden="true"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                    <span className="text-[11px] font-medium text-ink-primary">{event.kind}</span>
                    <span className="text-[10px] text-ink-tertiary">{event.node}</span>
                    <span className="tabular text-[10px] text-ink-tertiary">r{event.round}</span>
                    {event.messageId ? (
                      <button
                        type="button"
                        onClick={(clickEvent) => {
                          clickEvent.stopPropagation();
                          selectMessage(selected ? null : event.messageId);
                        }}
                        className="rounded font-mono text-[10px] text-network underline decoration-network/30 underline-offset-2 hover:decoration-network focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                        aria-pressed={selected}
                      >
                        {event.messageId}
                      </button>
                    ) : null}
                  </div>
                  {event.detail ? (
                    <p className="mt-0.5 line-clamp-2 break-words text-[11px] leading-relaxed text-ink-tertiary">
                      {event.detail}
                    </p>
                  ) : null}
                </div>
              </li>
            );
          })
        )}
      </ol>

      <footer className="border-t border-base-700 px-5 py-2.5">
        <p className="text-[10px] leading-relaxed text-ink-tertiary">
          Showing the newest 120 matching events that had happened by this point.
          {' '}Select an event to follow its message.
        </p>
      </footer>
    </section>
  );
}
