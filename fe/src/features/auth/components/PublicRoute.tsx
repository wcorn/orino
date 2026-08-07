import { Navigate, Outlet } from "react-router-dom";

import { useAuth } from "../../../app/providers";

export function PublicRoute() {
  const { isAuthenticated } = useAuth();
  // 로그인한 채로 /login에 들어오면 워크스페이스 선택으로 보낸다.
  // 마지막에 고른 워크스페이스를 기억하지 않으므로 항상 여기서 다시 고른다.
  return isAuthenticated ? <Navigate to="/select" replace /> : <Outlet />;
}
