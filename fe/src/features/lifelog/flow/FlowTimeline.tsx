import { ChevronDown, ChevronUp, ImageIcon, X } from "lucide-react";

import { Button } from "@/components/ui/button";

import type { MomentCard } from "../api/types";
import { formatDay, formatMomentTime, localDateKey } from "../lib/datetime";

interface FlowTimelineProps {
  moments: MomentCard[];
  onRemove: (momentId: number) => void;
  onReorder: (momentIds: number[]) => void;
}

/** 흐름 상세의 타임라인 뷰 — 날짜 구분 + 시간순 서사. 항목 빼기·순서 조정. */
export function FlowTimeline({
  moments,
  onRemove,
  onReorder,
}: FlowTimelineProps) {
  if (moments.length === 0) {
    return (
      <p className="text-muted-foreground py-12 text-center text-sm">
        아직 담긴 기록이 없어요. [기록 담기]로 추가하세요.
      </p>
    );
  }

  const move = (index: number, delta: number) => {
    const next = [...moments];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    onReorder(next.map((m) => m.id));
  };

  let lastDay = "";

  return (
    <ol className="flex flex-col gap-2">
      {moments.map((moment, index) => {
        const dayKey = localDateKey(moment.occurredAt);
        const showDay = dayKey !== lastDay;
        lastDay = dayKey;
        return (
          <li key={moment.id}>
            {showDay && (
              <p className="text-muted-foreground mt-3 mb-1 text-xs font-medium">
                {formatDay(moment.occurredAt)}
              </p>
            )}
            <div className="border-border flex items-center gap-3 rounded-lg border p-2">
              <div className="bg-muted text-muted-foreground flex size-12 shrink-0 items-center justify-center overflow-hidden rounded">
                {moment.photos[0] ? (
                  <img
                    src={moment.photos[0].thumbUrl ?? moment.photos[0].url}
                    alt=""
                    className="size-full object-cover"
                  />
                ) : (
                  <ImageIcon className="size-4" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-muted-foreground text-xs">
                  {formatMomentTime(moment.occurredAt)}
                </p>
                <p className="truncate text-sm">{moment.body ?? "(사진)"}</p>
              </div>
              <div className="flex items-center">
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label="위로"
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                >
                  <ChevronUp />
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label="아래로"
                  disabled={index === moments.length - 1}
                  onClick={() => move(index, 1)}
                >
                  <ChevronDown />
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label="흐름에서 빼기"
                  onClick={() => onRemove(moment.id)}
                >
                  <X />
                </Button>
              </div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
