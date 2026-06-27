import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";

import { useSaveWeeklyPlan, useWeeklyPlan } from "../../hooks/useWeeklyPlan";
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
  const [dirty, setDirty] = useState(false);
  const [editing, setEditing] = useState<EditableBlock | null>(null);
  const [mobileDay, setMobileDay] = useState(new Date().getDay());

  useEffect(() => {
    if (data) {
      setBlocks(toEditable(data));
      setDirty(false);
    }
  }, [data]);

  // 새 블록은 draft로 모달만 연다(아직 grid에 추가하지 않음).
  // 적용을 눌러야 확정되므로 backdrop/취소로 닫으면 누적되지 않는다.
  const handleCreate = (block: EditableBlock) => {
    setEditing(block);
  };

  // 적용: 기존 블록이면 교체, draft(미존재)면 추가(확정).
  const handleSaveBlock = (updated: EditableBlock) => {
    setBlocks((prev) =>
      prev.some((b) => b.key === updated.key)
        ? prev.map((b) => (b.key === updated.key ? updated : b))
        : [...prev, updated],
    );
    setDirty(true);
    setEditing(null);
  };

  const handleDeleteBlock = (key: string) => {
    setBlocks((prev) => prev.filter((b) => b.key !== key));
    setDirty(true);
    setEditing(null);
  };

  const handleSaveAll = () => {
    save.mutate(toInput(blocks), { onSuccess: () => setDirty(false) });
  };

  const days = narrow ? [mobileDay] : ALL_DAYS;

  return (
    <div className="flex flex-col gap-3 p-4">
      <header className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold">주간 계획표</h1>
          {dirty && (
            <p className="text-muted-foreground text-xs">
              저장되지 않은 변경이 있습니다
            </p>
          )}
        </div>
        <Button
          size="sm"
          onClick={handleSaveAll}
          disabled={!dirty || save.isPending}
        >
          {save.isPending ? "저장 중…" : "저장"}
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
        <>
          {blocks.length === 0 && (
            <p className="text-muted-foreground rounded-md border border-dashed p-4 text-center text-sm">
              빈 한 주입니다. 시간대를 눌러 블록을 추가하세요.
            </p>
          )}
          <WeeklyPlanGrid
            blocks={blocks}
            days={days}
            onCreate={handleCreate}
            onSelect={setEditing}
          />
        </>
      )}

      <PlanBlockEditor
        block={editing}
        onSave={handleSaveBlock}
        onDelete={handleDeleteBlock}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}
