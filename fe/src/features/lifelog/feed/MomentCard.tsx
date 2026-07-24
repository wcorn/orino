import { MapPin, MoreHorizontal } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Menu, MenuItem } from "@/components/ui/menu";

import type { MomentCard as MomentCardType } from "../api/types";
import { formatMomentTime } from "../lib/datetime";
import { moodEmoji } from "../lib/moods";
import { PhotoGrid } from "./PhotoGrid";

interface MomentCardProps {
  moment: MomentCardType;
  onEdit: (moment: MomentCardType) => void;
  onDelete: (moment: MomentCardType) => void;
}

export function MomentCard({ moment, onEdit, onDelete }: MomentCardProps) {
  const emoji = moodEmoji(moment.mood);

  return (
    <article className="border-border bg-background flex flex-col gap-3 rounded-xl border p-4">
      <PhotoGrid photos={moment.photos} />

      {moment.body && (
        <p className="text-sm whitespace-pre-wrap">{moment.body}</p>
      )}

      <div className="text-muted-foreground flex items-center gap-2 text-xs">
        {emoji && <span aria-hidden>{emoji}</span>}
        {moment.placeName && (
          <span className="inline-flex items-center gap-0.5">
            <MapPin className="size-3" />
            {moment.placeName}
          </span>
        )}
        <span>{formatMomentTime(moment.occurredAt)}</span>
        <span className="ml-auto">
          <Menu
            trigger={
              <Button size="icon-sm" variant="ghost" aria-label="기록 메뉴">
                <MoreHorizontal />
              </Button>
            }
          >
            <MenuItem onClick={() => onEdit(moment)}>편집</MenuItem>
            <MenuItem variant="destructive" onClick={() => onDelete(moment)}>
              삭제
            </MenuItem>
          </Menu>
        </span>
      </div>

      {moment.tags.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {moment.tags.map((tag) => (
            <span
              key={tag}
              className="bg-muted text-foreground/70 rounded-full px-2 py-0.5 text-xs"
            >
              #{tag}
            </span>
          ))}
        </div>
      )}
    </article>
  );
}
