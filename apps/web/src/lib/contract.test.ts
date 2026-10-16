import { describe, expect, it } from 'vitest';

import type { IncidentSummary } from './api';
import { percent } from './format';

/**
 * Contract tests against the real serialised shape of the backend.
 *
 * <p>These exist because of a defect the audit caught by reading rather than by
 * running. Spring is configured with `default-property-inclusion: non_null`, so
 * a null field is omitted from the JSON entirely and arrives in TypeScript as
 * `undefined`. Every nullable field was declared `| null` and every guard was
 * written `field !== null`, which is true for `undefined`.
 *
 * <p>The consequence was visible in the product: the handset never sends
 * `advisoryConfidence`, so every device-created incident rendered
 * "Classified on-device at NaN% confidence" in the responder queue.
 *
 * <p>The fixture below is the literal JSON the backend returned during manual
 * verification, not a hand-written approximation.
 */
describe('backend payload contract', () => {
  // Exactly what the gateway returned for a device-created incident during the
  // end-to-end check: no advisoryConfidence key at all.
  const deviceIncidentJson = `{
    "id": 7,
    "reference": "RQ-06F12C",
    "title": "Medical emergency reported (sent_to_peer)",
    "category": "MEDICAL",
    "categoryLabel": "Medical emergency",
    "severity": 3,
    "status": "NEW",
    "statusLabel": "New",
    "advisory": true,
    "locationShared": false,
    "sourceDeviceId": "rqn1_4f59672710e6d8e6568068b9b52a43ab",
    "createdAt": "2026-10-15T18:56:43.309721Z",
    "updatedAt": "2026-10-15T18:56:43.309721Z",
    "messageCount": 1
  }`;

  it('omits advisoryConfidence entirely rather than sending null', () => {
    const parsed = JSON.parse(deviceIncidentJson) as Record<string, unknown>;
    // The backend omits null fields, so an explicit null would be a different
    // contract and would hide this class of bug.
    expect('advisoryConfidence' in parsed).toBe(false);
  });

  it('does not produce NaN% for a missing confidence', () => {
    const incident = JSON.parse(deviceIncidentJson) as IncidentSummary;
    const rendered = percent(incident.advisoryConfidence);

    // A dash is an honest answer. NaN% is a defect shown to a responder.
    expect(rendered).not.toContain('NaN');
    expect(rendered).toBe('—');
  });

  it('treats an explicit null the same as an omitted field', () => {
    const explicitNull = JSON.parse('{"advisoryConfidence": null}') as IncidentSummary;
    const omitted = JSON.parse('{}') as IncidentSummary;

    expect(explicitNull.advisoryConfidence ?? null).toBeNull();
    expect(omitted.advisoryConfidence ?? null).toBeNull();
    expect(percent(omitted.advisoryConfidence)).toBe(percent(explicitNull.advisoryConfidence));
  });

  it('renders a real confidence unchanged', () => {
    const withConfidence = JSON.parse(
      '{"advisoryConfidence": 0.728}',
    ) as IncidentSummary;

    expect(percent(withConfidence.advisoryConfidence)).toBe('72.8%');
  });
});
