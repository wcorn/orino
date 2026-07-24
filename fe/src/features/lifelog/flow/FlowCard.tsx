import { ImageIcon } from "lucide-react";
import { Link } from "react-router-dom";

import type { FlowSummary } from "../api/flows";
import { formatFlowPeriod } from "../lib/datetime";

/** 흐름 목록의 카드. 커버·제목·기간·기록 수. */
export function FlowCard({ flow }: { flow: FlowSummary }) {
  const period = formatFlowPeriod(flow.startedAt, flow.endedAt);

  return (
    <Link
      to={`/lifelog/flows/${flow.id}`}
      className="border-border bg-background hover:border-primary/40 flex flex-col overflow-hidden rounded-xl border transition-colors"
    >
      <div className="bg-muted text-muted-foreground flex aspect-video items-center justify-center">
        {flow.coverUrl ? (
          <img
            src={flow.coverUrl}
            alt=""
            loading="lazy"
            className="size-full object-cover"
          />
        ) : (
          <ImageIcon className="size-8" />
        )}
      </div>
      <div className="flex flex-col gap-0.5 p-3">
        <span className="truncate text-sm font-medium">{flow.title}</span>
        <span className="text-muted-foreground text-xs">
          {period && <span>{period} · </span>}기록 {flow.momentCount}
        </span>
      </div>
    </Link>
  );
}
