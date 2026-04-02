import { useNavigate } from "react-router-dom";
import { logout } from "../features/auth/api/auth";
import { useAuth } from "../app/providers";

export function MainPage() {
  const navigate = useNavigate();
  const { refresh } = useAuth();

  const handleLogout = async () => {
    await logout();
    refresh();
    navigate("/", { replace: true });
  };

  return (
    <div className="main-container">
      <header className="main-header">
        <span className="main-logo">orino</span>
        <button onClick={handleLogout}>로그아웃</button>
      </header>
      <main className="main-content" />
    </div>
  );
}
