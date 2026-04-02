import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../../../app/providers";

export function PrivateRoute() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}
