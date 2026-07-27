import { ListChecks, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";
import { useIsNarrow } from "@/shared/lib/useIsNarrow";

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

/** 주간 계획표 페이지 본체. 서버 주간 템플릿을 로드해 로컬 편집 후 전량 교체로 저장한다. */
export function WeeklyPlan() {
  const { data, isLoading, isError } = useWeeklyPlan();
  const save = useSaveWeeklyPlan();
  const narrow = useIsNarrow();

  const [blocks, setBlocks] = useState<EditableBlock[]>([]);
  const [editing, setEditing] = useState<EditableBlock | null>(null);
  const [creating, setCreating] = useState(false);
  const [mobileDay, setMobileDay] = useState(new Date().getDay());
  // 다중 선택 삭제용. selecting=선택 모드, selectedKeys=체크된 블록 key.
  const [selecting, setSelecting] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());

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

  const exitSelecting = () => {
    setSelecting(false);
    setSelectedKeys(new Set());
  };

  // 선택 모드에서 블록 클릭 → 체크 토글.
  const toggleSelect = (block: EditableBlock) => {
    setSelectedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(block.key)) next.delete(block.key);
      else next.add(block.key);
      return next;
    });
  };

  // 체크된 블록을 한 번에 제거하고 1회만 전량 교체 저장한다.
  const handleDeleteSelected = () => {
    if (selectedKeys.size === 0) return;
    persist(blocks.filter((b) => !selectedKeys.has(b.key)));
    exitSelecting();
  };

  const days = narrow ? [mobileDay] : ALL_DAYS;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="주간 계획표"
        actions={
          selecting ? (
            <>
              <span className="text-muted-foreground text-sm">
                {selectedKeys.size}개 선택
              </span>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDeleteSelected}
                disabled={selectedKeys.size === 0}
              >
                <Trash2 className="size-4" />
                삭제
              </Button>
              <Button variant="outline" size="sm" onClick={exitSelecting}>
                취소
              </Button>
            </>
          ) : (
            <>
              {blocks.length > 0 && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setSelecting(true)}
                >
                  <ListChecks className="size-4" />
                  선택
                </Button>
              )}
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCreating(true)}
              >
                <Plus className="size-4" />
                추가
              </Button>
            </>
          )
        }
      />

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
        <FieldError className="p-6">
          주간 계획표를 불러오지 못했습니다.
        </FieldError>
      ) : (
        <WeeklyPlanGrid
          blocks={blocks}
          days={days}
          onSelect={setEditing}
          selecting={selecting}
          selectedKeys={selectedKeys}
          onToggleSelect={toggleSelect}
        />
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
