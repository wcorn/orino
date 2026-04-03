import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useLocation } from "react-router-dom";
import { reissue } from "../features/auth/api/auth";
import { getAccessToken } from "../features/auth/store/authStore";

const queryClient = new QueryClient();

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

const PUBLIC_ROUTES = ["/", "/login"];

function AuthProvider({ children }: { children: ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  const { pathname } = useLocation();

  useEffect(() => {
    if (getAccessToken()) {
      setLoading(false);
      return;
    }
    if (PUBLIC_ROUTES.includes(pathname)) {
      setLoading(false);
      return;
    }
    reissue().finally(() => setLoading(false));
  }, [version, pathname]);

  const isAuthenticated = getAccessToken() !== null;
  const refresh = () => setVersion((v) => v + 1);

  if (loading) return null;

  return (
    <AuthContext.Provider value={{ isAuthenticated, loading, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function Providers({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
