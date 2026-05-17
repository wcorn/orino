import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";

export function EmptyTodayState() {
  return (
    <div className="flex min-h-[40svh] flex-col items-center justify-center gap-4 text-center">
      <p className="text-foreground text-lg">오늘은 복습할 카드가 없어요! 🌱</p>
      <Link to="/home">
        <Button variant="outline">홈으로</Button>
      </Link>
    </div>
  );
}
