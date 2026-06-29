import { Button, EmptyState } from "orino-fe";

export function Default() {
  return (
    <EmptyState className="min-h-[200px]">
      <p className="text-muted-foreground text-sm">아직 등록된 학습 자료가 없습니다.</p>
      <Button size="sm">자료 추가</Button>
    </EmptyState>
  );
}
