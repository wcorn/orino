import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";

interface Props {
  count: number;
}

export function CompletionState({ count }: Props) {
  return (
    <EmptyState>
      <p className="text-foreground text-2xl font-medium">
        🎉 오늘 복습 {count}개 모두 완료!
      </p>
      <p className="text-muted-foreground text-sm">오늘도 수고하셨어요.</p>
      <div className="flex gap-2">
        <Link to="/home">
          <Button variant="outline">홈으로</Button>
        </Link>
        <Link to="/planner/materials">
          <Button>학습 자료 보기</Button>
        </Link>
      </div>
    </EmptyState>
  );
}
