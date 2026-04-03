import { ArrowRight } from "lucide-react";
import { Link, Navigate } from "react-router-dom";

import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import { useAuth } from "../app/providers";

export function LandingPage() {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/home" replace />;
  }

  return (
    <div className="flex min-h-svh items-center justify-center px-4">
      <div className="flex flex-col items-center gap-5 text-center">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">
          orino
        </h1>
        <p className="text-muted-foreground text-sm">
          나만의 학습 플래너로 효율적인 공부를 시작하세요.
        </p>
        <Link
          to="/login"
          className={cn(
            buttonVariants({ variant: "default", size: "lg" }),
            "mt-2 gap-2 px-6 text-sm",
          )}
        >
          시작하기
          <ArrowRight className="size-3.5" />
        </Link>
      </div>
    </div>
  );
}
