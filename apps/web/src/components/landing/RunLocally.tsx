import { Copy, Terminal } from 'lucide-react';
import { useState } from 'react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionHeading } from '@/components/primitives/SectionHeading';

const COMMANDS = [
  {
    label: 'Mesh core and simulator tests',
    command: 'mvn test',
    note: '177 JVM tests, no network required',
  },
  {
    label: 'A reproducible simulated incident',
    command:
      'mvn -q -pl packages/mesh-simulator exec:java \\\n' +
      '  -Dexec.args="--topology ring --nodes 10 --messages 40 --seed 42"',
    note: 'Same seed, same event stream, every time',
  },
  {
    label: 'A partitioned network that recovers',
    command:
      'mvn -q -pl packages/mesh-simulator exec:java \\\n' +
      '  -Dexec.args="--topology two-cluster --nodes 10 --partitioned --churn 6"',
    note: 'The failure case, also deterministic',
  },
  {
    label: 'The Android application',
    command: 'cd apps/android && ./gradlew :app:installDebug',
    note: 'Requires a device with Google Play services',
  },
  {
    label: 'This site',
    command: 'cd apps/web && npm install && npm run dev',
    note: 'http://localhost:5173',
  },
];

export function RunLocally() {
  const [copied, setCopied] = useState<string | null>(null);

  const copy = async (command: string) => {
    try {
      await navigator.clipboard.writeText(command);
      setCopied(command);
      window.setTimeout(() => setCopied(null), 1600);
    } catch {
      // Clipboard access can be refused. The command is visible on screen, so a
      // failed copy is a lost convenience rather than a dead end.
    }
  };

  return (
    <section id="run-it-locally" className="scroll-mt-20 border-b border-base-600 py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <SectionHeading
          eyebrow="Run it yourself"
          title="Everything above is reproducible from a clean checkout"
          body="No paid services, no cloud account, no API keys. The mesh and the simulator need nothing but a JDK."
        />

        <div className="mt-12 space-y-3">
          {COMMANDS.map((entry, index) => (
            <Reveal key={entry.label} delay={index * 0.04}>
              <div className="surface group flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:gap-5">
                <div className="flex min-w-0 flex-1 flex-col gap-2">
                  <div className="flex items-center gap-2">
                    <Terminal size={14} className="shrink-0 text-network" aria-hidden="true" />
                    <span className="text-sm font-medium text-ink-primary">{entry.label}</span>
                  </div>
                  <pre className="overflow-x-auto whitespace-pre text-[12.5px] leading-relaxed text-ink-secondary">
                    <code>{entry.command}</code>
                  </pre>
                  <p className="text-xs text-ink-tertiary">{entry.note}</p>
                </div>
                <button
                  type="button"
                  onClick={() => void copy(entry.command)}
                  className="inline-flex shrink-0 items-center justify-center gap-1.5 rounded-lg border border-base-600 px-3 py-2 text-xs font-medium text-ink-tertiary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                >
                  <Copy size={13} aria-hidden="true" />
                  {copied === entry.command ? 'Copied' : 'Copy'}
                </button>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
