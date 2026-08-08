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
import { hadSession } from "../features/auth/sessionMarker";
import { getAccessToken } from "../features/auth/store/authStore";
import { installFocusRevalidation } from "./focusRevalidation";
import { prefetchRoutes } from "./routeImports";

// 복귀(visibilitychange + window focus) 시 활성 쿼리를 재검증하도록 focus 신호를 구독한다.
installFocusRevalidation();

interface AuthContextType {
  isAuthenticated: boolean;
  /**
   * 토큰 없이 캐시로만 도는 상태(#1095). 네트워크가 없어 재발급을 못 했지만 이 기기에서
   * 로그인한 적은 있는 경우다. 조회는 되고 편집은 어차피 오프라인에서 막혀 있다(§4.6).
   */
  offlineSession: boolean;
  loading: boolean;
  refresh: () => void;
}

const AuthContext = createContext<AuthContextType>({
  isAuthenticated: false,
  offlineSession: false,
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
  const [offlineSession, setOfflineSession] = useState(false);
  const { pathname } = useLocation();
  const refresh = () => setVersion((v) => v + 1);

  useEffect(() => {
    if (getAccessToken()) {
      setOfflineSession(false);
      setLoading(false);
      return;
    }
    if (PUBLIC_ROUTES.includes(pathname)) {
      setLoading(false);
      return;
    }
    reissue()
      .then((result) => {
        // 서버에 닿지 못했을 뿐이고 이 기기에서 로그인한 적이 있다면, 캐시로 돌게 둔다.
        // 여기서 로그인 화면으로 보내면 캐시에 든 일정을 끝내 못 본다(#1095).
        setOfflineSession(result === "offline" && hadSession());
      })
      .finally(() => setLoading(false));
  }, [version, pathname]);

  // 토큰이 있거나, 없더라도 오프라인 세션이면 앱을 보여준다.
  const isAuthenticated = getAccessToken() !== null || offlineSession;

  /**
   * 네트워크가 돌아오면 곧바로 다시 물어본다.
   *
   * <p>오프라인 세션은 <b>임시 상태</b>다. 리프레시 토큰이 진짜로 만료됐다면 그 사실은
   * 온라인이 돼야 알 수 있고, 그때 정상적으로 로그아웃된다.
   *
   * <p><b>{@code online} 이벤트만 믿지 않는다.</b> 폰에서 앱을 접어 뒀다 다시 여는 사이에
   * 네트워크가 돌아오면 그 이벤트를 못 받는다 — 화면에 돌아올 때도 한 번 확인한다.
   */
  useEffect(() => {
    if (!offlineSession) return;
    const retry = () => refresh();
    const onVisible = () => {
      if (document.visibilityState === "visible") retry();
    };
    window.addEventListener("online", retry);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener("online", retry);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [offlineSession]);

  // 로그인 상태면 idle 시간에 페이지 청크를 미리 받아 진입 지연을 없앤다.
  useEffect(() => {
    if (isAuthenticated) prefetchRoutes();
  }, [isAuthenticated]);

  if (loading) return null;

  return (
    <AuthContext.Provider
      value={{ isAuthenticated, offlineSession, loading, refresh }}
    >
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
