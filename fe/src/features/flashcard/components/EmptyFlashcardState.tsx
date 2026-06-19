import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";

interface Props {
  onAdd: () => void;
}

export function EmptyFlashcardState({ onAdd }: Props) {
  return (
    <EmptyState className="min-h-[30svh]">
      <p className="text-muted-foreground text-sm">아직 카드가 없습니다.</p>
      <Button onClick={onAdd}>
        <Plus className="size-4" /> 첫 카드 추가
      </Button>
    </EmptyState>
  );
}
