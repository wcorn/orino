import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";

interface EmptyMaterialStateProps {
  onAdd: () => void;
}

export function EmptyMaterialState({ onAdd }: EmptyMaterialStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-20">
      <p className="text-muted-foreground text-sm">
        아직 등록된 학습 자료가 없습니다.
      </p>
      <Button onClick={onAdd}>
        <Plus className="size-4" />첫 자료 추가
      </Button>
    </div>
  );
}
