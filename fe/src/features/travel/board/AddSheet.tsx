import { Archive, ArrowLeft, PenLine, Search } from "lucide-react";
import { type FormEvent, useEffect, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import type { Activity } from "@/features/travel/api/activities";
import { useBoard } from "@/features/travel/hooks/useBoard";

type Mode = "menu" | "manual";

interface AddSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tripId: number;
  /** 담을 날짜. null이면 보관함에 넣는다. */
  targetDate: string | null;
  onCreate: (input: { title: string; startTime: string | null }) => void;
  /** 보관함 일정을 이 날짜로 옮긴다. */
  onPickFromArchive: (activity: Activity) => void;
  /** 장소 검색(S-06)으로 나간다. */
  onSearchPlaces: () => void;
  pending?: boolean;
}

/**
 * 일정 추가 시트. 장소 검색 · 직접 입력 · 보관함에서 가져오기 세 갈래다.
 *
 * <p>장소 검색은 시트 안에 넣지 않고 화면(S-06)으로 나간다 — 결과 20개와 날짜 선택 시트가
 * 겹쳐 뜨면 시트 위에 시트가 쌓인다.
 */
export function AddSheet({
  open,
  onOpenChange,
  tripId,
  targetDate,
  onCreate,
  onPickFromArchive,
  onSearchPlaces,
  pending = false,
}: AddSheetProps) {
  const [mode, setMode] = useState<Mode>("menu");
  const [title, setTitle] = useState("");
  const [startTime, setStartTime] = useState("");

  // 열 때마다 처음 상태로 되돌린다 — 지난번 입력이 남아 있으면 실수로 저장된다.
  useEffect(() => {
    if (open) {
      setMode("menu");
      setTitle("");
      setStartTime("");
    }
  }, [open]);

  // 보관함을 보고 있을 땐 "가져오기"가 의미 없다(이미 거기 있다).
  const canPickFromArchive = targetDate !== null;
  const { data: archiveBoard } = useBoard(tripId, { archive: true });
  const archived = canPickFromArchive ? (archiveBoard?.activities ?? []) : [];

  const submitManual = (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim()) return;
    onCreate({ title: title.trim(), startTime: startTime || null });
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title={mode === "manual" ? "직접 입력" : "일정 추가"}
      description={targetDate === null ? "미배정 보관함에 담습니다" : undefined}
    >
      {mode === "menu" ? (
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={onSearchPlaces}
            className="border-border hover:bg-accent flex items-center gap-2 rounded-lg border px-3 py-2.5 text-left text-sm"
          >
            <Search className="size-4 shrink-0" />
            <span className="flex-1">장소 검색</span>
          </button>

          <button
            type="button"
            onClick={() => setMode("manual")}
            className="border-border hover:bg-accent flex items-center gap-2 rounded-lg border px-3 py-2.5 text-left text-sm"
          >
            <PenLine className="size-4 shrink-0" />
            직접 입력
          </button>

          {canPickFromArchive && (
            <div className="flex flex-col gap-1.5">
              <p className="text-muted-foreground mt-2 flex items-center gap-1.5 text-xs">
                <Archive className="size-3.5" />
                보관함에서 가져오기
              </p>
              {archived.length === 0 ? (
                <p className="text-muted-foreground px-1 text-[13px]">
                  보관함이 비어 있어요.
                </p>
              ) : (
                archived.map((activity) => (
                  <button
                    key={activity.id}
                    type="button"
                    onClick={() => onPickFromArchive(activity)}
                    disabled={pending}
                    className="border-border hover:bg-accent rounded-lg border px-3 py-2.5 text-left text-sm"
                  >
                    {activity.title}
                  </button>
                ))
              )}
            </div>
          )}
        </div>
      ) : (
        <form className="flex flex-col gap-3" onSubmit={submitManual}>
          <FormField label="일정 제목" htmlFor="activityTitle">
            <Input
              id="activityTitle"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="센소지"
              maxLength={100}
              autoFocus
            />
          </FormField>
          <FormField label="시각 (선택)" htmlFor="activityTime">
            <Input
              id="activityTime"
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
            />
          </FormField>
          <div className="flex justify-between gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setMode("menu")}
            >
              <ArrowLeft className="size-4" />
              뒤로
            </Button>
            <Button type="submit" disabled={pending || !title.trim()}>
              추가
            </Button>
          </div>
        </form>
      )}
    </BottomSheet>
  );
}
