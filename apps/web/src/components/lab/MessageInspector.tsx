import { ArrowRight, CornerDownRight, Info, Radio } from 'lucide-react';
import { useMemo, useState } from 'react';

import { useLabStore } from '@/lab/labStore';
import {
  JOURNEY_OUTCOME_LABELS,
  buildJourney,
  type JourneyOutcome,
} from '@/lab/replay';

/** A select with hundreds of thousands of options is slower than the parse. */
const MAX_MESSAGE_OPTIONS = 500;

/** Keep one enormous journey from putting 200 000 rows in the DOM. */
const MAX_VISIBLE_STEPS = 200;

const OUTCOME_TONE: Record<JourneyOutcome, string> = {
  acknowledged: 'border-delivered/45 bg-delivered/[0.08] text-delivered',
  delivered: 'border-network/45 bg-network/[0.08] text-network',
  rejected: 'border-emergency/45 bg-emergency/[0.08] text-emergency',
  expired: 'border-pending/45 bg-pending/[0.08] text-pending',
  failed: 'border-emergency/45 bg-emergency/[0.08] text-emergency',
  'in-flight': 'border-base-500 bg-base-800 text-ink-secondary',
  unknown: 'border-base-500 bg-base-800 text-ink-tertiary',
};

/**
 * One message, reconstructed only from its recorded events.
 *
 * <p>The inspector does not draw a route. A flood can carry several copies at
 * once, and the report records handoffs rather than a single path, so the
 * steps are listed in order and labelled for what they are.
 */
export function MessageInspector() {
  const model = useLabStore((state) => state.model);
  const cursor = useLabStore((state) => state.cursor);
  const selectedMessageId = useLabStore((state) => state.selectedMessageId);
  const selectMessage = useLabStore((state) => state.selectMessage);
  const seek = useLabStore((state) => state.seek);
  const [messageFilter, setMessageFilter] = useState('');

  const journey = useMemo(() => {
    if (!model || !selectedMessageId) return null;
    return buildJourney(model, selectedMessageId, { untilEventIndex: cursor });
  }, [model, selectedMessageId, cursor]);
  const visibleSteps = journey ? journey.steps.slice(-MAX_VISIBLE_STEPS) : [];
  const hiddenSteps = journey ? journey.steps.length - visibleSteps.length : 0;

  const messageGroups = useMemo(() => {
    const composed: string[] = [];
    const eventOnly: string[] = [];
    if (!model) return { composed, eventOnly };
    for (const id of model.messageIds) {
      if (model.messagesById.has(id)) composed.push(id);
      else eventOnly.push(id);
    }
    return { composed, eventOnly };
  }, [model]);

  if (!model) return null;

  const message = selectedMessageId ? model.messagesById.get(selectedMessageId) : undefined;
  const query = messageFilter.trim().toLowerCase();
  const matchesFilter = (id: string): boolean =>
    query === '' || id.toLowerCase().includes(query);
  const composedMatches =
    query === '' ? messageGroups.composed : messageGroups.composed.filter(matchesFilter);
  const eventOnlyMatches =
    query === '' ? messageGroups.eventOnly : messageGroups.eventOnly.filter(matchesFilter);
  const composedShown = composedMatches.slice(0, MAX_MESSAGE_OPTIONS);
  const eventOnlyShown = eventOnlyMatches.slice(0, MAX_MESSAGE_OPTIONS);
  const selectedShown =
    selectedMessageId !== null &&
    (composedShown.includes(selectedMessageId) || eventOnlyShown.includes(selectedMessageId));
  const shownCount = composedShown.length + eventOnlyShown.length;
  const matchingCount = composedMatches.length + eventOnlyMatches.length;

  const optionLabel = (id: string): string => {
    const entry = model.messagesById.get(id);
    const route = entry
      ? entry.broadcast
        ? 'broadcast'
        : `${entry.origin ?? '?'} → ${entry.destination ?? '?'}`
      : 'provenance not recorded';
    return `${id} · ${route}`;
  };

  const optionFor = (id: string) => (
    <option key={id} value={id}>
      {optionLabel(id)}
    </option>
  );

  return (
    <section className="surface flex flex-col" aria-labelledby="message-inspector-title">
      <header className="border-b border-base-700 px-5 py-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="message-inspector-title" className="text-sm font-medium text-ink-primary">
            Message journey
          </h2>
          <span className="eyebrow">{messageGroups.composed.length} composed</span>
        </div>

        {model.messageIds.length > 20 ? (
          <input
            type="search"
            value={messageFilter}
            onChange={(event) => setMessageFilter(event.target.value)}
            placeholder="Filter message ids"
            aria-label="Filter message ids"
            className="mt-3 w-full rounded-lg border border-base-600 bg-base-900 px-2.5 py-1.5 text-[11px] text-ink-primary placeholder:text-ink-tertiary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
          />
        ) : null}

        <label htmlFor="message-select" className="sr-only">
          Message to inspect
        </label>
        <select
          id="message-select"
          value={selectedMessageId ?? ''}
          onChange={(event) => selectMessage(event.target.value || null)}
          className="mt-3 w-full rounded-lg border border-base-600 bg-base-900 px-2.5 py-2 text-[12px] text-ink-primary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
        >
          <option value="">Select a message…</option>
          {selectedMessageId !== null && !selectedShown ? (
            <option value={selectedMessageId}>{optionLabel(selectedMessageId)} · selected</option>
          ) : null}
          {composedShown.length > 0 ? (
            <optgroup label="Composed messages">{composedShown.map(optionFor)}</optgroup>
          ) : null}
          {eventOnlyShown.length > 0 ? (
            <optgroup label="Referenced in events only">{eventOnlyShown.map(optionFor)}</optgroup>
          ) : null}
        </select>
        {matchingCount > shownCount ? (
          <p className="mt-1.5 text-[10px] leading-relaxed text-ink-tertiary">
            Showing {shownCount} of {matchingCount.toLocaleString('en-US')} matching ids. Type above
            to narrow the list.
          </p>
        ) : null}
        {messageGroups.eventOnly.length > 0 ? (
          <p className="mt-1.5 text-[10px] leading-relaxed text-ink-tertiary">
            {messageGroups.eventOnly.length.toLocaleString('en-US')} further ids appear in the
            event stream — acknowledgements and relays — but this report does not record who
            composed them.
          </p>
        ) : null}
      </header>

      {!journey ? (
        <div className="px-5 py-10 text-center">
          <Radio size={20} className="mx-auto text-ink-tertiary" aria-hidden="true" />
          <p className="mt-2 text-[12px] text-ink-tertiary">
            Choose a message above, or select one from the event stream.
          </p>
        </div>
      ) : (
        <div className="min-h-0 flex-1 px-5 py-4">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-widest ${OUTCOME_TONE[journey.outcome]}`}
            >
              {JOURNEY_OUTCOME_LABELS[journey.outcome]}
            </span>
            <span className="font-mono text-[11px] text-ink-secondary">{journey.messageId}</span>
            {journey.broadcast ? (
              <span className="rounded-full border border-base-600 px-2 py-0.5 text-[10px] text-ink-tertiary">
                broadcast
              </span>
            ) : null}
          </div>

          <dl className="mt-4 grid grid-cols-2 gap-3 text-[11px]">
            <div>
              <dt className="eyebrow">Origin</dt>
              <dd className="mt-0.5 text-ink-secondary">
                {journey.origin === null ? 'not recorded' : `node ${journey.origin}`}
              </dd>
            </div>
            <div>
              <dt className="eyebrow">Destination</dt>
              <dd className="mt-0.5 text-ink-secondary">
                {journey.broadcast
                  ? 'everyone in range'
                  : journey.destination === null
                    ? 'not recorded'
                    : `node ${journey.destination}`}
              </dd>
            </div>
            <div>
              <dt className="eyebrow">Composed</dt>
              <dd className="tabular mt-0.5 text-ink-secondary">
                {message?.originatedAt == null
                  ? 'not recorded'
                  : `+${((message.originatedAt - model.startMillis) / 1000).toFixed(2)} s`}
              </dd>
            </div>
            <div>
              <dt className="eyebrow">Recorded handoffs</dt>
              <dd className="tabular mt-0.5 text-ink-secondary">
                {journey.handoffs.length}
                <span className="ml-1 text-ink-tertiary">
                  ({journey.handoffs.filter((handoff) => handoff.kind === 'accepted').length}{' '}
                  accepted)
                </span>
              </dd>
            </div>
          </dl>

          {!message ? (
            <p className="mt-3 flex items-start gap-1.5 rounded-lg border border-pending/30 bg-pending/[0.05] px-2.5 py-2 text-[10px] leading-relaxed text-pending">
              <Info size={11} className="mt-0.5 shrink-0" aria-hidden="true" />
              This report predates message provenance, so the origin and destination are not
              guessed.
            </p>
          ) : null}

          <h3 className="eyebrow mt-5">Recorded steps up to this point</h3>
          {hiddenSteps > 0 ? (
            <p className="mt-1.5 text-[10px] leading-relaxed text-ink-tertiary">
              Showing the last {visibleSteps.length} of{' '}
              {journey.steps.length.toLocaleString('en-US')} recorded steps. Earlier steps are
              not rendered so a very large journey cannot freeze the tab; search the event stream
              for an earlier record.
            </p>
          ) : null}
          {journey.steps.length === 0 ? (
            <p className="mt-2 rounded-lg border border-base-600 px-3 py-4 text-center text-[11px] text-ink-tertiary">
              Nothing has happened to this message yet.
            </p>
          ) : (
            <ol className="mt-2 max-h-[300px] space-y-0 overflow-y-auto border-l border-base-600 pl-4">
              {visibleSteps.map((step, index) => {
                const last = index === visibleSteps.length - 1;
                const position = model.eventOrder.get(step.event) ?? -1;
                return (
                  <li key={`${step.event.atEpochMillis}-${step.event.nodeIndex}-${index}`} className="relative pb-3">
                    <span
                      className={
                        'absolute -left-[21px] top-1 h-2 w-2 rounded-full border-2 border-base-850 ' +
                        (last ? 'bg-network' : 'bg-base-500')
                      }
                      aria-hidden="true"
                    />
                    <div className="flex items-baseline justify-between gap-2">
                      <span className="tabular font-mono text-[10px] text-ink-tertiary">
                        +{((step.event.atEpochMillis - model.startMillis) / 1000).toFixed(2)}s
                      </span>
                      <button
                        type="button"
                        onClick={() => {
                          if (position >= 0) seek(position + 1);
                        }}
                        className="inline-flex items-center gap-1 text-[10px] font-medium text-ink-tertiary transition-colors hover:text-network focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                        aria-label={`Jump the replay to event ${position + 1}, ${step.event.kind} at ${step.event.node}`}
                      >
                        jump
                        <CornerDownRight size={10} aria-hidden="true" />
                      </button>
                    </div>
                    <p className="mt-0.5 text-[11px] leading-relaxed text-ink-secondary">
                      {step.description}
                    </p>
                    {step.peers.length > 0 ? (
                      <p className="mt-0.5 flex items-center gap-1 text-[10px] text-ink-tertiary">
                        <ArrowRight size={9} aria-hidden="true" />
                        peers: {step.peers.map((peer) => `node ${peer}`).join(', ')}
                      </p>
                    ) : null}
                  </li>
                );
              })}
            </ol>
          )}

          <div className="mt-4 rounded-lg border border-base-700 bg-base-900/60 px-3 py-2.5">
            <p className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-ink-tertiary">
              <Info size={11} aria-hidden="true" />
              What this view cannot say
            </p>
            <ul className="mt-1.5 space-y-1">
              {journey.limitations.map((limitation) => (
                <li
                  key={limitation}
                  className="text-[10px] leading-relaxed text-ink-tertiary"
                >
                  {limitation}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </section>
  );
}
