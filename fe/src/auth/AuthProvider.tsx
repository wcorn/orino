import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { getAccessToken } from "./authStore";
import { reissue } from "../api/auth";

interface AuthContextType {
  isAuthenticated: boolean;
  loading: boolean;
  refresh: () => void;
}

const AuthContext = createContext<AuthContextType>({
  isAuthenticated: false,
  loading: true,
  refresh: () => {},
});

export function useAuth(): AuthContextType {
  return useContext(AuthContext);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (getAccessToken()) {
      setLoading(false);
      return;
    }
    reissue().finally(() => setLoading(false));
  }, [version]);

  const isAuthenticated = getAccessToken() !== null;
  const refresh = () => setVersion((v) => v + 1);

  if (loading) {
    return null;
  }

  return (
    <AuthContext.Provider value={{ isAuthenticated, loading, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}
