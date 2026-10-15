import { motion } from 'framer-motion';
import { HeartPulse, LogIn } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { Button } from '@/components/primitives/Button';
import { useAuthStore } from '@/state/auth';

/**
 * Responder sign-in.
 *
 * <p>No self-service registration, deliberately. Anyone who can reach the
 * gateway should not be able to create themselves a view of emergency reports,
 * so accounts are provisioned by an administrator.
 */
export function SignInPage() {
  const navigate = useNavigate();
  const signIn = useAuthStore((state) => state.signIn);
  const signingIn = useAuthStore((state) => state.signingIn);
  const error = useAuthStore((state) => state.error);
  const token = useAuthStore((state) => state.token);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  useEffect(() => {
    if (token) navigate('/command', { replace: true });
  }, [token, navigate]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const ok = await signIn(username, password);
    if (ok) navigate('/command', { replace: true });
  };

  return (
    <div className="grid min-h-screen bg-base-900 lg:grid-cols-2">
      <div className="relative hidden overflow-hidden border-r border-base-700 lg:block">
        <div
          className="absolute inset-0 opacity-60"
          style={{
            background:
              'radial-gradient(80% 70% at 30% 20%, rgba(46,211,230,0.14), transparent 65%)',
          }}
          aria-hidden="true"
        />
        <div className="relative flex h-full flex-col justify-between p-12">
          <div className="flex items-center gap-2.5">
            <HeartPulse size={19} className="text-emergency" aria-hidden="true" />
            <span className="text-sm font-semibold tracking-tight text-ink-primary">ResQNet</span>
          </div>
          <div className="max-w-md">
            <h2 className="text-3xl font-semibold leading-tight tracking-tightest text-ink-primary">
              The mesh keeps working when the network does not. This is where
              somebody looks at the result.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-ink-secondary">
              Everything shown here is either a genuine device report or a
              deterministic simulation run, and the two are never presented as
              each other.
            </p>
          </div>
          <p className="text-xs text-ink-tertiary">
            Experimental software. Not a replacement for emergency services.
          </p>
        </div>
      </div>

      <div className="flex items-center justify-center px-6 py-12">
        <motion.form
          onSubmit={submit}
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
          className="w-full max-w-sm"
        >
          <h1 className="text-2xl font-semibold tracking-tight text-ink-primary">Sign in</h1>
          <p className="mt-2 text-sm text-ink-secondary">
            Responder accounts are created by an administrator.
          </p>

          <div className="mt-8 flex flex-col gap-4">
            <Field
              id="username"
              label="Username"
              value={username}
              onChange={setUsername}
              autoComplete="username"
            />
            <Field
              id="password"
              label="Password"
              type="password"
              value={password}
              onChange={setPassword}
              autoComplete="current-password"
            />
          </div>

          {error ? (
            <p
              role="alert"
              className="mt-4 rounded-lg border border-emergency/30 bg-emergency-soft px-3.5 py-2.5 text-[13px] text-emergency"
            >
              {error}
            </p>
          ) : null}

          <Button type="submit" disabled={signingIn} className="mt-6 w-full">
            <LogIn size={16} aria-hidden="true" />
            {signingIn ? 'Signing in…' : 'Sign in'}
          </Button>

          <a
            href="/"
            className="mt-6 block text-center text-xs text-ink-tertiary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            Back to the public site
          </a>
        </motion.form>
      </div>
    </div>
  );
}

function Field({
  id,
  label,
  value,
  onChange,
  type = 'text',
  autoComplete,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-medium text-ink-secondary">
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        required
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        className="min-h-[44px] rounded-xl border border-base-600 bg-base-850 px-3.5 text-sm text-ink-primary transition-colors placeholder:text-ink-tertiary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
      />
    </div>
  );
}
