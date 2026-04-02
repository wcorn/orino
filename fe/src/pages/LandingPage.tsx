import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../app/providers";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function LandingPage() {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/home" replace />;
  }

  return (
    <div className="flex min-h-svh items-center justify-center px-4">
      <div className="flex flex-col items-center gap-6 text-center">
        <h1 className="text-5xl font-extrabold tracking-tight text-foreground">
          orino
        </h1>
        <p className="text-base text-muted-foreground leading-relaxed">
          나만의 학습 플래너로 효율적인 공부를 시작하세요.
        </p>
        <Link
          to="/login"
          className={cn(buttonVariants({ size: "lg" }), "px-8")}
        >
          로그인하기
        </Link>
      </div>
    </div>
  );
}
