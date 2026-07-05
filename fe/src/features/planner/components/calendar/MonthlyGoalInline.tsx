import { Popover } from "@base-ui/react/popover";
import { Plus, Target } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

import { useMonthlyGoal } from "../../hooks/useMonthlyGoal";
import { useMonthlyGoalMutations } from "../../hooks/useMonthlyGoalMutations";

interface Props {
  year: number;
  month: number;
}

/**
 * 월 뷰 헤더 년월 옆 인라인 월간 목표. 없으면 플레이스홀더, 있으면 아이콘+말줄임.
 * 클릭 시 팝오버(textarea)로 편집한다. 내용을 비우고 저장하면 삭제한다.
 */
export function MonthlyGoalInline({ year, month }: Props) {
  const { data: goal } = useMonthlyGoal(year, month);
  const { save, remove } = useMonthlyGoalMutations(year, month);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");

  // 팝오버 열 때 현재 목표로 초기화
  const handleOpenChange = (next: boolean) => {
    if (next) setDraft(goal?.content ?? "");
    setOpen(next);
  };

  const handleSave = () => {
    if (draft.trim().length === 0) {
      // 비우고 저장 → 기존 목표 삭제(없으면 그냥 닫기)
      if (goal) remove.mutate(undefined, { onSuccess: () => setOpen(false) });
      else setOpen(false);
      return;
    }
    save.mutate(draft, { onSuccess: () => setOpen(false) });
  };

  const handleDelete = () => {
    remove.mutate(undefined, { onSuccess: () => setOpen(false) });
  };

  const pending = save.isPending || remove.isPending;
  const failed = save.isError || remove.isError;

  return (
    <Popover.Root open={open} onOpenChange={handleOpenChange}>
      <Popover.Trigger
        render={
          goal ? (
            <button
              type="button"
              aria-label={`이번 달 목표: ${goal.content}`}
              title={goal.content}
              className="text-foreground/80 hover:bg-muted flex max-w-[10rem] items-center gap-1.5 rounded-md px-2 py-1 text-sm sm:max-w-[16rem]"
            >
              <Target className="text-primary size-3.5 shrink-0" />
              <span className="truncate">{goal.content}</span>
            </button>
          ) : (
            <button
              type="button"
              aria-label="이번 달 목표 추가"
              className="text-muted-foreground hover:bg-muted hover:text-foreground flex items-center gap-1 rounded-md px-2 py-1 text-sm"
            >
              <Plus className="size-3.5" /> 이번 달 목표
            </button>
          )
        }
      />
      <Popover.Portal>
        <Popover.Positioner sideOffset={6} align="start" className="z-50">
          <Popover.Popup className="bg-popover text-popover-foreground w-72 rounded-md border p-3 shadow-md">
            <p className="text-muted-foreground mb-1.5 text-xs font-medium">
              {year}년 {month}월 목표
            </p>
            <Textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              rows={3}
              maxLength={1000}
              autoFocus
              aria-label="이번 달 목표 내용"
              placeholder="이번 달 목표를 적어보세요"
            />
            {failed && (
              <p className="text-destructive mt-1 text-xs">
                저장에 실패했어요. 다시 시도해 주세요.
              </p>
            )}
            <div className="mt-2 flex items-center justify-between gap-2">
              {goal ? (
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-destructive"
                  disabled={pending}
                  onClick={handleDelete}
                >
                  삭제
                </Button>
              ) : (
                <span />
              )}
              <Button size="sm" disabled={pending} onClick={handleSave}>
                저장
              </Button>
            </div>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  );
}
