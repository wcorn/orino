import { Archive, Bell, MapPin, Star, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import type { Activity } from "@/features/travel/api/activities";

interface ActivityRowProps {
  activity: Activity;
  /** 보관함을 보고 있으면 "보관함으로" 액션을 감춘다(이미 거기 있다). */
  inArchive: boolean;
  onArchive: (activity: Activity) => void;
  onDelete: (activity: Activity) => void;
}

/**
 * 일정 한 줄. 시각·본문·액션 3열 그리드다.
 *
 * <p>시각이 없는 일정이 정상이라(§1.1) 빈칸 대신 `──`를 둔다 — 자리를 비우면 본문이
 * 좌우로 흔들려 목록이 읽기 어려워진다.
 */
export function ActivityRow({
  activity,
  inArchive,
  onArchive,
  onDelete,
}: ActivityRowProps) {
  return (
    <li className="hover:bg-muted grid grid-cols-[52px_1fr_auto] items-start gap-2 rounded-lg px-2 py-2.5">
      <span
        className={
          activity.startTime
            ? "pt-px text-sm tabular-nums"
            : "text-muted-foreground pt-px text-sm"
        }
      >
        {activity.startTime ?? "──"}
      </span>

      <Link
        to={`/travel/activities/${activity.id}`}
        className="min-w-0 no-underline"
      >
        <span className="block text-[15px] leading-[1.4]">
          {activity.title}
        </span>
        {activity.place && (
          <span className="text-muted-foreground flex items-center gap-1 text-xs">
            <MapPin className="size-3 shrink-0" />
            <span className="truncate">{activity.place.name}</span>
          </span>
        )}
      </Link>

      <span className="flex items-center gap-0.5">
        {activity.notifyEnabled && (
          <Bell aria-label="알림 켜짐" className="text-primary size-3.5" />
        )}
        {activity.hasLog && (
          <Star
            aria-label="기록 있음"
            className="text-muted-foreground size-3.5"
          />
        )}
        {!inArchive && (
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`${activity.title} 보관함으로`}
            onClick={() => onArchive(activity)}
          >
            <Archive className="size-4" />
          </Button>
        )}
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={`${activity.title} 삭제`}
          onClick={() => onDelete(activity)}
        >
          <Trash2 className="size-4" />
        </Button>
      </span>
    </li>
  );
}
