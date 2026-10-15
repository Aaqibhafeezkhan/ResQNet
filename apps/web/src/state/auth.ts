import { create } from 'zustand';
import { api, authStorage, type LoginResponse } from '@/lib/api';

/**
 * Session state.
 *
 * Deliberately not persisted beyond the token itself. A responder console that
 * reopens with stale incident data on a shared machine is a problem; clearing
 * the cached view on sign-out costs one refetch.
 */
interface AuthState {
  token: string | null;
  identity: LoginResponse | null;
  signingIn: boolean;
  error: string | null;
  signIn: (username: string, password: string) => Promise<boolean>;
  signOut: () => void;
  restore: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: authStorage.token(),
  identity: authStorage.identity(),
  signingIn: false,
  error: null,

  async signIn(username, password) {
    set({ signingIn: true, error: null });
    try {
      const identity = await api.login(username, password);
      authStorage.save(identity.token, identity);
      set({ token: identity.token, identity, signingIn: false, error: null });
      return true;
    } catch (cause) {
      set({
        signingIn: false,
        error: cause instanceof Error ? cause.message : 'Sign-in failed',
      });
      return false;
    }
  },

  signOut() {
    authStorage.clear();
    set({ token: null, identity: null, error: null });
  },

  restore() {
    set({ token: authStorage.token(), identity: authStorage.identity() });
  },
}));

/** True when this account may change incident state. */
export function canMutate(role: string | undefined): boolean {
  return role === 'RESPONDER' || role === 'ADMIN';
}
