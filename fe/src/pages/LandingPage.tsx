import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../app/providers";

export function LandingPage() {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/home" replace />;
  }

  return (
    <div className="landing-container">
      <div className="landing-content">
        <h1 className="landing-title">orino</h1>
        <p className="landing-description">
          나만의 학습 플래너로 효율적인 공부를 시작하세요.
        </p>
        <Link to="/login" className="landing-login-button">
          로그인하기
        </Link>
      </div>
    </div>
  );
}
