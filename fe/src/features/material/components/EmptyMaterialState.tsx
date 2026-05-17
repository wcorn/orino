import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";

interface Props {
  onAdd: () => void;
}

export function EmptyMaterialState({ onAdd }: Props) {
  return (
    <div className="flex min-h-[40svh] flex-col items-center justify-center gap-4 text-center">
      <p className="text-muted-foreground text-sm">
        아직 등록된 학습 자료가 없습니다.
      </p>
      <Button onClick={onAdd}>
        <Plus className="size-4" /> 첫 자료 추가
      </Button>
    </div>
  );
}
