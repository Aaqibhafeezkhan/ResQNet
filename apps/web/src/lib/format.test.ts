import { describe, expect, it } from 'vitest';

import {
  clockTime,
  deliveryStateLabel,
  deliveryStateTone,
  millis,
  percent,
  relativeTime,
  severityLabel,
  shortId,
} from './format';

/**
 * Formatting is where honesty is easiest to lose. A dash and a zero are very
 * different claims, and these tests pin the difference.
 */
describe('format', () => {
  describe('millis', () => {
    it('renders a missing measurement as a dash, never as zero', () => {
      expect(millis(null)).toBe('—');
      expect(millis(undefined)).toBe('—');
      expect(millis(-1)).toBe('—');
      expect(millis(NaN)).toBe('—');
    });

    it('renders sub-second values in milliseconds', () => {
      expect(millis(0)).toBe('0 ms');
      expect(millis(375)).toBe('375 ms');
      expect(millis(999)).toBe('999 ms');
    });

    it('renders longer values in seconds', () => {
      expect(millis(1000)).toBe('1.00 s');
      expect(millis(4125)).toBe('4.13 s');
    });
  });

  describe('percent', () => {
    it('refuses to invent a rate', () => {
      expect(percent(null)).toBe('—');
      expect(percent(undefined)).toBe('—');
      expect(percent(NaN)).toBe('—');
    });

    it('renders a real rate', () => {
      expect(percent(0)).toBe('0.0%');
      expect(percent(0.775)).toBe('77.5%');
      expect(percent(1)).toBe('100.0%');
    });
  });

  describe('relativeTime', () => {
    const now = Date.parse('2026-01-01T12:00:00Z');

    it('handles an absent or unparseable timestamp', () => {
      expect(relativeTime(null, now)).toBe('—');
      expect(relativeTime('not a date', now)).toBe('—');
    });

    it('describes recent times as just now', () => {
      expect(relativeTime('2026-01-01T11:59:58Z', now)).toBe('just now');
    });

    it('collapses anything past a minute', () => {
      expect(relativeTime('2026-01-01T11:59:30Z', now)).toBe('30s ago');
      expect(relativeTime('2026-01-01T11:58:00Z', now)).toBe('2m ago');
      expect(relativeTime('2026-01-01T09:00:00Z', now)).toBe('3h ago');
    });

    it('handles a clock skewed into the future', () => {
      expect(relativeTime('2026-01-01T12:05:00Z', now)).toBe('in 5m');
    });
  });

  describe('deliveryStateLabel', () => {
    it('keeps "handed over" distinct from "confirmed"', () => {
      expect(deliveryStateLabel('SENT_TO_PEER')).toBe('Handed to a nearby device');
      expect(deliveryStateLabel('DELIVERED')).toBe('Received');
      expect(deliveryStateLabel('ACKNOWLEDGED')).toBe('Confirmed received');
    });

    it('does not present a terminal failure as a success', () => {
      expect(deliveryStateLabel('EXPIRED')).toBe('Too old to send');
      expect(deliveryStateLabel('FAILED')).toBe('Could not be sent');
    });

    it('degrades gracefully for an unknown state', () => {
      expect(deliveryStateLabel('SOME_NEW_STATE')).toBe('some new state');
    });
  });

  describe('deliveryStateTone', () => {
    it('maps confirmation to the delivered tone', () => {
      expect(deliveryStateTone('ACKNOWLEDGED')).toBe('delivered');
      expect(deliveryStateTone('DELIVERED')).toBe('delivered');
    });

    it('maps a failure to the emergency tone', () => {
      expect(deliveryStateTone('EXPIRED')).toBe('emergency');
      expect(deliveryStateTone('FAILED')).toBe('emergency');
    });

    it('maps everything in flight to the pending tone', () => {
      expect(deliveryStateTone('QUEUED')).toBe('pending');
      expect(deliveryStateTone('SENT_TO_PEER')).toBe('pending');
      expect(deliveryStateTone('CREATED')).toBe('pending');
    });
  });

  describe('severityLabel', () => {
    it('gives the number a word', () => {
      expect(severityLabel(3)).toBe('Critical');
      expect(severityLabel(2)).toBe('Elevated');
      expect(severityLabel(1)).toBe('Routine');
      expect(severityLabel(0)).toBe('Unclassified');
    });
  });

  it('shortens an identifier without losing its recognisable head', () => {
    expect(shortId('rqn1_0123456789abcdef', 10)).toBe('rqn1_01234…');
    expect(shortId('short', 10)).toBe('short');
    expect(shortId(null)).toBe('—');
  });

  it('formats a clock time and tolerates junk', () => {
    expect(clockTime(null)).toBe('—');
    expect(clockTime('nope')).toBe('—');
    expect(clockTime('2026-01-01T12:34:00Z')).toMatch(/\d{2}:\d{2}/);
  });
});
