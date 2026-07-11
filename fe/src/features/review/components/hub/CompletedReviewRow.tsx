import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";

import type { CompletedReviewItem } from "../../api/reviewHub";
import { formatCompletedLabel } from "../../time";
import { GRADE_BADGE } from "./labels";

export function CompletedReviewRow({ item }: { item: CompletedReviewItem }) {
  const badge = GRADE_BADGE[item.rating];
  return (
    <Card size="sm">
      <CardContent className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{item.flashcard.front}</p>
          <p className="text-muted-foreground text-xs">
            {item.flashcard.material.title}
          </p>
        </div>
        <Badge variant={badge.variant} className="shrink-0">
          {badge.label}
        </Badge>
        <span className="text-muted-foreground shrink-0 tabular-nums">
          {formatCompletedLabel(item.completedAt)}
        </span>
      </CardContent>
    </Card>
  );
}
