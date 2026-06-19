import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";

interface Props {
  onAdd: () => void;
}

export function EmptyMaterialState({ onAdd }: Props) {
  return (
    <EmptyState>
      <p className="text-muted-foreground text-sm">
        아직 등록된 학습 자료가 없습니다.
      </p>
      <Button onClick={onAdd}>
        <Plus className="size-4" /> 첫 자료 추가
      </Button>
    </EmptyState>
  );
}
