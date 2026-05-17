import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";

interface Props {
  onAdd: () => void;
}

export function EmptyFlashcardState({ onAdd }: Props) {
  return (
    <div className="flex min-h-[30svh] flex-col items-center justify-center gap-4 text-center">
      <p className="text-muted-foreground text-sm">아직 카드가 없습니다.</p>
      <Button onClick={onAdd}>
        <Plus className="size-4" /> 첫 카드 추가
      </Button>
    </div>
  );
}
