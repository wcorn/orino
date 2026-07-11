import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

import type {
  ReviewScope,
  ReviewSummary,
  ReviewSummaryMaterial,
} from "../../api/reviewHub";

/** 상태 행/스코프 진입 대상. */
export type StatusTarget = "now" | "overdue" | "upcoming" | "done";

interface ReviewRailProps {
  summary: ReviewSummary;
  activeTab: "upcoming" | "completed";
  scope: ReviewScope;
  activeMaterialId?: number;
  onStatusSelect: (target: StatusTarget) => void;
  onStartAll: () => void;
  onStartOverdue: () => void;
  onSelectMaterial: (material: ReviewSummaryMaterial) => void;
}

export function ReviewRail({
  summary,
  activeTab,
  scope,
  activeMaterialId,
  onStatusSelect,
  onStartAll,
  onStartOverdue,
  onSelectMaterial,
}: ReviewRailProps) {
  const { counts, estimatedMinutes, materials } = summary;

  const statusRows: {
    key: StatusTarget;
    label: string;
    dot: string;
    count: number;
    active: boolean;
  }[] = [
    {
      key: "now",
      label: "지금 할 것",
      dot: "bg-primary",
      count: counts.now,
      active: activeTab === "upcoming" && scope === "today",
    },
    {
      key: "overdue",
      label: "밀림",
      dot: "bg-warning",
      count: counts.overdue,
      active: activeTab === "upcoming" && scope === "overdue",
    },
    {
      key: "upcoming",
      label: "앞으로",
      dot: "bg-info",
      count: counts.upcoming,
      active: activeTab === "upcoming" && scope === "all",
    },
    {
      key: "done",
      label: "오늘 완료",
      dot: "bg-success",
      count: counts.doneToday,
      active: activeTab === "completed",
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      {/* CTA */}
      <Card>
        <CardContent className="flex flex-col gap-3">
          <p className="text-sm">
            <span className="font-medium">지금 복습 {counts.now}장</span>{" "}
            <span className="text-muted-foreground">
              · 약 {estimatedMinutes}분
            </span>
          </p>
          <Button onClick={onStartAll} disabled={counts.now === 0}>
            전체 복습 시작
          </Button>
          <Button
            variant="outline"
            onClick={onStartOverdue}
            disabled={counts.overdue === 0}
          >
            밀린 것만{counts.overdue > 0 ? ` ${counts.overdue}` : ""}
          </Button>
        </CardContent>
      </Card>

      {/* 상태 4행 */}
      <Card>
        <CardContent className="flex flex-col">
          {statusRows.map((row) => (
            <button
              key={row.key}
              type="button"
              onClick={() => onStatusSelect(row.key)}
              aria-pressed={row.active}
              className={cn(
                "hover:bg-accent/60 flex items-center justify-between rounded-md px-2 py-2 text-sm transition-colors",
                row.active && "bg-accent",
              )}
            >
              <span className="flex items-center gap-2">
                <span className={cn("size-2 rounded-full", row.dot)} />
                {row.label}
              </span>
              <span className="text-base font-semibold tabular-nums">
                {row.count}
              </span>
            </button>
          ))}
        </CardContent>
      </Card>

      {/* 자료별 */}
      <Card>
        <CardContent className="flex flex-col gap-1">
          <p className="text-muted-foreground px-2 text-xs font-medium">
            자료별
          </p>
          {materials.length === 0 ? (
            <p className="text-muted-foreground px-2 py-1 text-sm">
              복습할 자료가 없어요
            </p>
          ) : (
            materials.map((m) => (
              <button
                key={m.id}
                type="button"
                onClick={() => onSelectMaterial(m)}
                disabled={m.due === 0 && m.overdue === 0}
                className={cn(
                  "hover:bg-accent/60 flex items-center justify-between rounded-md px-2 py-2 text-left text-sm transition-colors disabled:opacity-50 disabled:hover:bg-transparent",
                  activeMaterialId === m.id && "bg-accent",
                )}
              >
                <span className="flex min-w-0 items-center gap-2">
                  {m.overdue > 0 && (
                    <span
                      className="bg-warning size-2 shrink-0 rounded-full"
                      aria-label="밀린 카드 있음"
                    />
                  )}
                  <span className="truncate">{m.name}</span>
                </span>
                <span className="text-muted-foreground shrink-0 tabular-nums">
                  {m.due > 0 ? m.due : "–"}
                </span>
              </button>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
