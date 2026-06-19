import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useState,
} from "react";
import { useLocation } from "react-router-dom";

import { reissue } from "../features/auth/api/auth";
import { getAccessToken } from "../features/auth/store/authStore";
import { installFocusRevalidation } from "./focusRevalidation";
import { prefetchRoutes } from "./routeImports";

// 복귀(visibilitychange + window focus) 시 활성 쿼리를 재검증하도록 focus 신호를 구독한다.
installFocusRevalidation();

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

  // 로그인 상태면 idle 시간에 페이지 청크를 미리 받아 진입 지연을 없앤다.
  useEffect(() => {
    if (isAuthenticated) prefetchRoutes();
  }, [isAuthenticated]);

  if (loading) return null;

  return (
    <AuthContext.Provider value={{ isAuthenticated, loading, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
            // 탭/페이지 왕복 시 즉시 재요청하지 않도록 기본 staleTime을 둔다.
            // (개별 훅에서 staleTime: Infinity 등으로 덮어쓸 수 있음)
            staleTime: 60 * 1000,
            gcTime: 10 * 60 * 1000,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
