import { create } from 'zustand';

import { login as loginApi, logout as logoutApi, me, refresh } from '@/api/endpoints/authentication/authentication';
import type { MeResponse } from '@/api/models/meResponse';
import { subscribeSessionExpired, tokenStore } from '@/api/tokenStore';
import { queryClient } from '@/lib/queryClient';

import type { AuthStatus } from './authTypes';

interface AuthState {
  status: AuthStatus;
  profile: MeResponse | null;
  bootstrap: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  handleSessionExpired: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'loading',
  profile: null,

  /** Restores the session on startup via the HttpOnly refresh cookie, then loads /me. */
  bootstrap: async () => {
    try {
      const session = await refresh();
      if (!session.accessToken || session.accessToken.length === 0) {
        throw new Error('Refresh returned no access token');
      }
      tokenStore.set(session.accessToken);
      const profile = await me();
      set({ profile, status: 'authenticated' });
    } catch {
      tokenStore.clear();
      queryClient.clear();
      set({ profile: null, status: 'unauthenticated' });
    }
  },

  login: async (email, password) => {
    const response = await loginApi({ email, password });
    const accessToken = response.accessToken;
    if (!accessToken || accessToken.length === 0) {
      throw new Error('Login response missing access token');
    }
    tokenStore.set(accessToken);
    try {
      const profile = await me();
      // Stale data cached by a previous session must never surface after a new login.
      queryClient.clear();
      set({ profile, status: 'authenticated' });
    } catch (error) {
      tokenStore.clear();
      set({ profile: null, status: 'unauthenticated' });
      throw error;
    }
  },

  logout: async () => {
    try {
      // Invalidate before the network call so an in-flight refresh cannot resurrect the token
      // while (or after) the server-side revocation runs.
      tokenStore.invalidateSession();
      await logoutApi();
    } catch {
      // Server-side revocation may fail; the local session must still be cleared.
    } finally {
      tokenStore.clear();
      queryClient.clear();
      set({ profile: null, status: 'unauthenticated' });
    }
  },

  handleSessionExpired: () => {
    tokenStore.clear();
    // The session is dead: no queued refresh may resurrect it and no cached clinical data may
    // survive for the next user.
    tokenStore.invalidateSession();
    queryClient.clear();
    set({ profile: null, status: 'unauthenticated' });
  },
}));

// A failed refresh (stale/revoked session) must reset the session state from anywhere.
subscribeSessionExpired(() => useAuthStore.getState().handleSessionExpired());
