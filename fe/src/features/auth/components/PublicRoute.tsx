import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../../../app/providers";

export function PublicRoute() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />;
}
