import { Pencil } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

import type { Flashcard } from "../api/flashcards";
import { formatNextReview } from "../utils";

interface Props {
  index: number;
  flashcard: Flashcard;
  onEdit: () => void;
}

export function FlashcardItem({ index, flashcard, onEdit }: Props) {
  return (
    <Card>
      <CardContent className="flex items-start justify-between gap-4">
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground text-xs font-medium">
              📇 #{index + 1}
            </span>
            {flashcard.nextReview && (
              <span className="text-muted-foreground text-xs">
                다음 복습: {formatNextReview(flashcard.nextReview)}
              </span>
            )}
          </div>
          <p className="text-sm font-medium whitespace-pre-line">
            {flashcard.front}
          </p>
          <p className="text-muted-foreground text-xs whitespace-pre-line">
            뒤: {flashcard.back}
          </p>
        </div>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={`카드 ${index + 1} 편집`}
          onClick={onEdit}
        >
          <Pencil className="size-4" />
        </Button>
      </CardContent>
    </Card>
  );
}
