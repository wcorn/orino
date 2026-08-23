import { History } from "lucide-react";

import { cn } from "@/lib/utils";

import type { TargetHistoryEntry } from "../api/shortlink";

interface TargetHistoryListProps {
  history: TargetHistoryEntry[];
}

/**
 * 목적지 교체 이력(SL-008). 시간 역순이고 <b>마지막 줄이 최초 발급</b>이다.
 *
 * <p><b>첫 줄에는 취소선을 넣지 않는다</b> — 그게 지금 살아 있는 목적지다. 과거 항목만
 * 흐리게 긋는다(화면 설계 §0 정정).
 *
 * <p>이 목록이 이 화면의 존재 이유다. 주소는 그대로인데 목적지가 몇 번 갈렸는지가 여기 남고,
 * 그게 "한 번 뿌린 링크를 죽지 않게 한다"는 말의 실체다.
 */
export function TargetHistoryList({ history }: TargetHistoryListProps) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <h2 className="text-caption text-muted-foreground flex items-center gap-1.5 font-semibold">
        <History className="size-3.5" />
        목적지 교체 이력
      </h2>
      <ol aria-label="목적지 교체 이력" className="flex flex-col gap-2.5">
        {history.map((entry, index) => (
          <li
            key={`${entry.changedAt}-${index}`}
            className={cn(
              "flex flex-col gap-0.5",
              // 현재 목적지(첫 줄)는 그대로 둔다.
              index > 0 && "opacity-70",
            )}
          >
            <span
              className={cn(
                "truncate text-[13px]",
                index > 0 && "line-through",
              )}
            >
              {entry.targetUrl}
            </span>
            <span className="text-muted-foreground text-xs">
              {formatDate(entry.changedAt)}
              {entry.reason ? ` — ${entry.reason}` : ""}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}

function formatDate(isoDateTime: string): string {
  const date = new Date(isoDateTime);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}.${month}.${day}`;
}
