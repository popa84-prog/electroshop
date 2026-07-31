import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import authService from '../api/authService';
import { tokenStore } from '../api/axios';
import { rolesHave } from '../utils/permissions';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On first load, if we have a token, fetch the current profile.
  useEffect(() => {
    const bootstrap = async () => {
      if (tokenStore.getAccess()) {
        try {
          const profile = await authService.me();
          setUser(profile);
        } catch {
          tokenStore.clear();
          setUser(null);
        }
      }
      setLoading(false);
    };
    bootstrap();
  }, []);

  const handleAuthPayload = (data) => {
    tokenStore.set(data.accessToken, data.refreshToken);
    setUser({
      id: data.userId,
      fullName: data.fullName,
      email: data.email,
      roles: data.roles,
    });
  };

  // Feature #6: when the account has 2FA enabled, the API returns
  // { requiresTwoFactor: true, twoFactorToken } instead of real tokens — no
  // session is created yet, so the page must show a code-entry step next.
  const login = async (credentials) => {
    const data = await authService.login(credentials);
    if (!data.requiresTwoFactor) {
      handleAuthPayload(data);
    }
    return data;
  };

  const verifyTwoFactor = async (twoFactorToken, code) => {
    const data = await authService.verifyTwoFactor({ twoFactorToken, code });
    handleAuthPayload(data);
    return data;
  };

  // Registration no longer logs the user in: new accounts are pending admin
  // approval. Returns the API response so the page can show the pending message.
  const register = async (payload) => {
    return authService.register(payload);
  };

  const logout = () => {
    tokenStore.clear();
    setUser(null);
  };

  // "Deconectează toate sesiunile" (feature #6) — revokes every token issued so
  // far server-side, then also clears this device's local session.
  const logoutAllSessions = async () => {
    try {
      await authService.logoutAll();
    } finally {
      logout();
    }
  };

  const value = useMemo(
    () => ({
      user,
      loading,
      isAuthenticated: !!user,
      isAdmin: !!user?.roles?.includes('ROLE_ADMIN'),
      // Feature #6: granular admin-panel permission check, e.g. hasPermission('PRODUCTS_DELETE').
      // UX convenience only — the backend enforces the real gate on every request.
      hasPermission: (permission) => rolesHave(user?.roles || [], permission),
      login,
      verifyTwoFactor,
      register,
      logout,
      logoutAllSessions,
    }),
    [user, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
