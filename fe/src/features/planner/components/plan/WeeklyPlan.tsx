import { Plus } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";

import { useSaveWeeklyPlan, useWeeklyPlan } from "../../hooks/useWeeklyPlan";
import { PlanBlockCreate } from "./PlanBlockCreate";
import { PlanBlockEditor } from "./PlanBlockEditor";
import {
  DAY_LABELS,
  type EditableBlock,
  toEditable,
  toInput,
} from "./planGrid";
import { WeeklyPlanGrid } from "./WeeklyPlanGrid";

const ALL_DAYS = [0, 1, 2, 3, 4, 5, 6];

/** 화면 폭이 좁으면(모바일) 1일 뷰로 전환. matchMedia 미지원 환경(테스트)은 데스크탑으로 간주. */
function useIsNarrow(): boolean {
  const [narrow, setNarrow] = useState(
    () =>
      typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia("(max-width: 767px)").matches,
  );
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const mq = window.matchMedia("(max-width: 767px)");
    const handler = () => setNarrow(mq.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);
  return narrow;
}

/** 주간 계획표 페이지 본체. 서버 주간 템플릿을 로드해 로컬 편집 후 전량 교체로 저장한다. */
export function WeeklyPlan() {
  const { data, isLoading, isError } = useWeeklyPlan();
  const save = useSaveWeeklyPlan();
  const narrow = useIsNarrow();

  const [blocks, setBlocks] = useState<EditableBlock[]>([]);
  const [editing, setEditing] = useState<EditableBlock | null>(null);
  const [creating, setCreating] = useState(false);
  const [mobileDay, setMobileDay] = useState(new Date().getDay());

  useEffect(() => {
    if (data) setBlocks(toEditable(data));
  }, [data]);

  // 변경을 로컬에 즉시 반영하고 전량 교체로 자동 저장한다(저장 버튼 없음).
  const persist = (next: EditableBlock[]) => {
    setBlocks(next);
    save.mutate(toInput(next));
  };

  // + 버튼 생성: 선택한 여러 요일에 블록을 한 번에 추가.
  const handleCreate = (created: EditableBlock[]) => {
    persist([...blocks, ...created]);
    setCreating(false);
  };

  const handleSaveBlock = (updated: EditableBlock) => {
    persist(blocks.map((b) => (b.key === updated.key ? updated : b)));
    setEditing(null);
  };

  const handleDeleteBlock = (key: string) => {
    persist(blocks.filter((b) => b.key !== key));
    setEditing(null);
  };

  const days = narrow ? [mobileDay] : ALL_DAYS;

  return (
    <div className="flex flex-col gap-3 p-4">
      <header className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">주간 계획표</h1>
          <p className="text-muted-foreground text-xs">
            {save.isPending ? "저장 중…" : "변경 시 자동 저장됩니다"}
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => setCreating(true)}>
          <Plus className="size-4" />
          추가
        </Button>
      </header>

      {narrow && (
        <div className="flex gap-1" role="tablist" aria-label="요일 선택">
          {DAY_LABELS.map((label, i) => (
            <button
              key={label}
              type="button"
              role="tab"
              aria-selected={mobileDay === i}
              onClick={() => setMobileDay(i)}
              className={cn(
                "flex-1 rounded-md py-1.5 text-sm font-medium",
                mobileDay === i
                  ? "bg-primary/10 text-primary"
                  : "text-foreground/70 hover:bg-muted",
              )}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      {isLoading ? (
        <LoadingText className="p-6" />
      ) : isError ? (
        <p className="text-destructive p-6 text-sm">
          주간 계획표를 불러오지 못했습니다.
        </p>
      ) : (
        <WeeklyPlanGrid blocks={blocks} days={days} onSelect={setEditing} />
      )}

      <PlanBlockCreate
        open={creating}
        onCreate={handleCreate}
        onClose={() => setCreating(false)}
      />

      <PlanBlockEditor
        block={editing}
        onSave={handleSaveBlock}
        onDelete={handleDeleteBlock}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}
