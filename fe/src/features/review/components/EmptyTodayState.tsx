import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";

export function EmptyTodayState() {
  return (
    <EmptyState>
      <p className="text-foreground text-lg">오늘은 복습할 카드가 없어요! 🌱</p>
      <Link to="/home">
        <Button variant="outline">홈으로</Button>
      </Link>
    </EmptyState>
  );
}
