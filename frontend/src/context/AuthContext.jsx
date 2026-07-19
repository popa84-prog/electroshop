import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import authService from '../api/authService';
import { tokenStore } from '../api/axios';

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

  const login = async (credentials) => {
    const data = await authService.login(credentials);
    handleAuthPayload(data);
    return data;
  };

  const register = async (payload) => {
    const data = await authService.register(payload);
    handleAuthPayload(data);
    return data;
  };

  const logout = () => {
    tokenStore.clear();
    setUser(null);
  };

  const value = useMemo(
    () => ({
      user,
      loading,
      isAuthenticated: !!user,
      isAdmin: !!user?.roles?.includes('ROLE_ADMIN'),
      login,
      register,
      logout,
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
