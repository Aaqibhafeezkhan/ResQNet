import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { IncidentSummary } from '@/lib/api';

import { MapPanel } from './MapPanel';

function incident(overrides: Partial<IncidentSummary> = {}): IncidentSummary {
  return {
    id: 1,
    reference: 'INC-VALID',
    title: 'Synthetic incident',
    category: 'MEDICAL',
    categoryLabel: 'Medical',
    severity: 3,
    status: 'NEW',
    statusLabel: 'New',
    advisory: false,
    locationShared: true,
    latitude: 12,
    longitude: 34,
    createdAt: '2026-10-20T10:00:00Z',
    updatedAt: '2026-10-20T10:00:00Z',
    messageCount: 0,
    ...overrides,
  };
}

describe('MapPanel shared coordinates', () => {
  const context = {
    setTransform: vi.fn(),
    fillRect: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    stroke: vi.fn(),
    arc: vi.fn(),
    fill: vi.fn(),
    fillText: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(
      context as unknown as CanvasRenderingContext2D,
    );
    vi.spyOn(HTMLCanvasElement.prototype, 'getBoundingClientRect').mockReturnValue(
      { x: 0, y: 0, width: 640, height: 360, top: 0, right: 640, bottom: 360, left: 0, toJSON: () => ({}) },
    );
  });

  const invalidCoordinates = [
    ['null', null],
    ['NaN', NaN],
    ['positive infinity', Infinity],
    ['negative infinity', -Infinity],
  ] as const;
  const invalidCases: Array<{
    label: string;
    coordinates: Partial<IncidentSummary>;
    missing?: 'latitude' | 'longitude';
  }> = [
    ...invalidCoordinates.flatMap(([label, value]) => [
      { label: `${label} latitude`, coordinates: { latitude: value } },
      { label: `${label} longitude`, coordinates: { longitude: value } },
    ]),
    { label: 'latitude below -90', coordinates: { latitude: -90.01 } },
    { label: 'latitude above 90', coordinates: { latitude: 90.01 } },
    { label: 'longitude below -180', coordinates: { longitude: -180.01 } },
    { label: 'longitude above 180', coordinates: { longitude: 180.01 } },
    { label: 'undefined latitude', coordinates: {}, missing: 'latitude' },
    { label: 'undefined longitude', coordinates: {}, missing: 'longitude' },
  ];

  it.each(invalidCases)('reports $label as unlocated without drawing or selecting it', ({ coordinates, missing }) => {
    const onSelect = vi.fn();
    const invalid = incident({ reference: 'INC-INVALID', ...coordinates });
    if (missing) delete invalid[missing];
    render(
      <MapPanel
        incidents={[invalid, incident()]}
        selected={null}
        onSelect={onSelect}
      />,
    );

    const canvas = screen.getByRole('listbox', { name: 'Map of 1 incident locations shared by senders' });
    const plotted = screen.getByRole('list', { name: 'Incidents plotted on the map' });
    expect(within(plotted).getAllByRole('button')).toHaveLength(1);
    expect(within(plotted).queryByText(/INC-INVALID/)).toBeNull();
    expect(screen.getByText('1 without location')).toBeDefined();
    expect(context.fillText.mock.calls.map(([reference]) => reference)).toEqual(['INC-VALID']);
    expect(context.arc).toHaveBeenCalledTimes(2);
    fireEvent.keyDown(canvas, { key: 'Enter' });
    expect(onSelect).toHaveBeenCalledWith('INC-VALID');
  });

  it.each([
    { latitude: 12, longitude: 34 },
    { latitude: 0, longitude: 0 },
    { latitude: -90, longitude: -180 },
    { latitude: -90, longitude: 180 },
    { latitude: 90, longitude: -180 },
    { latitude: 90, longitude: 180 },
  ])('plots a shared point at ($latitude, $longitude)', (coordinates) => {
    render(<MapPanel incidents={[incident(coordinates)]} selected={null} onSelect={vi.fn()} />);

    expect(screen.getByRole('listbox', { name: 'Map of 1 incident locations shared by senders' })).toBeDefined();
    expect(screen.queryByText(/without location/)).toBeNull();
    expect(context.fillText.mock.calls.map(([reference]) => reference)).toEqual(['INC-VALID']);
    expect(context.arc).toHaveBeenCalledTimes(2);
  });

  it('keeps unshared coordinates off the map and reports them as unlocated', () => {
    render(
      <MapPanel incidents={[incident({ locationShared: false })]} selected={null} onSelect={vi.fn()} />,
    );

    expect(screen.getByRole('listbox', { name: 'Map of 0 incident locations shared by senders' })).toBeDefined();
    expect(screen.getByText('No locations shared')).toBeDefined();
    expect(screen.getByText('1 without location')).toBeDefined();
    expect(screen.queryByRole('button', { name: /INC-VALID/ })).toBeNull();
    expect(context.arc).not.toHaveBeenCalled();
    expect(context.fillText).not.toHaveBeenCalled();
  });
});
