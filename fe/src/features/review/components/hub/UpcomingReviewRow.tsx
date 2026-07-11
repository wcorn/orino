import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

import type { UpcomingReviewItem } from "../../api/reviewHub";
import { formatUpcomingLabel } from "../../time";
import { CARD_TYPE_LABEL } from "./labels";

export function UpcomingReviewRow({ item }: { item: UpcomingReviewItem }) {
  const emphasize = item.whenKind === "now";
  return (
    <Card size="sm">
      <CardContent className="flex items-center gap-3">
        <Badge variant="outline" className="shrink-0">
          {CARD_TYPE_LABEL[item.cardType]}
        </Badge>
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{item.flashcard.front}</p>
          <p className="text-muted-foreground text-xs">
            {item.flashcard.material.title}
          </p>
        </div>
        {item.overdue && (
          <Badge variant="warning" className="shrink-0">
            밀림
          </Badge>
        )}
        <span
          className={cn(
            "shrink-0 tabular-nums",
            emphasize ? "text-primary font-medium" : "text-muted-foreground",
          )}
        >
          {formatUpcomingLabel(item.scheduledAt)}
        </span>
      </CardContent>
    </Card>
  );
}
