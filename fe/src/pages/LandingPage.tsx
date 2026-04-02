import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../app/providers";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ArrowRight } from "lucide-react";

export function LandingPage() {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/home" replace />;
  }

  return (
    <div className="flex min-h-svh items-center justify-center px-4">
      <div className="flex flex-col items-center gap-5 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-foreground">
          orino
        </h1>
        <p className="text-sm text-muted-foreground">
          나만의 학습 플래너로 효율적인 공부를 시작하세요.
        </p>
        <Link
          to="/login"
          className={cn(
            buttonVariants({ variant: "default", size: "lg" }),
            "mt-2 gap-2 px-6 text-sm"
          )}
        >
          시작하기
          <ArrowRight className="size-3.5" />
        </Link>
      </div>
    </div>
  );
}
