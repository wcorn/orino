import { type FormEvent, useEffect, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Select, type SelectOption } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { BoardDay } from "@/features/travel/api/activities";

/** 날짜 자리에 쓰는 `보관함` 값. 날짜 문자열과 섞이지 않는 값이어야 한다. */
const ARCHIVE = "archive";

export interface PlaceAddInput {
  title: string;
  /** null이면 보관함이다. */
  date: string | null;
  startTime: string | null;
  memo: string | null;
}

interface PlaceAddSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 담을 장소 이름. 제목의 기본값이다. */
  placeName: string | null;
  days: BoardDay[];
  /** 처음 골라 둘 날짜 — <b>보고 있던 날짜</b>다. null이면 보관함. */
  defaultDate: string | null;
  onSave: (input: PlaceAddInput) => void;
  pending?: boolean;
}

/**
 * 장소를 일정으로 담는 시트(§S-06).
 *
 * <p><b>고르자마자 담지 않는다.</b> 검색 결과를 누르는 순간 담기면, 시각이나 메모를 넣으려면
 * 보드로 돌아가 그 일정을 다시 찾아 열어야 한다 — 담는 김에 적는 것이 자연스럽다.
 * 저장을 누를 때만 만든다.
 *
 * <p>날짜는 <b>보고 있던 날짜로 미리 채운다.</b> 3일차를 짜다가 검색으로 들어왔으면 담을 곳도
 * 3일차다. 매번 고르게 하면 같은 답을 반복해서 입력하게 된다. 물론 바꿀 수 있고,
 * `보관함`도 그대로 선택지에 있다 — "가고 싶다"는 "언제 갈지"보다 먼저 정해지기 때문이다.
 */
export function PlaceAddSheet({
  open,
  onOpenChange,
  placeName,
  days,
  defaultDate,
  onSave,
  pending = false,
}: PlaceAddSheetProps) {
  const [title, setTitle] = useState("");
  const [date, setDate] = useState<string>(ARCHIVE);
  const [startTime, setStartTime] = useState("");
  const [memo, setMemo] = useState("");

  // 열 때마다 이 장소·이 날짜로 되돌린다 — 지난번 입력이 남아 있으면 실수로 저장된다.
  useEffect(() => {
    if (!open) return;
    setTitle(placeName ?? "");
    setDate(defaultDate ?? ARCHIVE);
    setStartTime("");
    setMemo("");
  }, [open, placeName, defaultDate]);

  const options: SelectOption<string>[] = [
    ...days.map((day) => ({
      value: day.date,
      // 어느 도시의 날짜인지가 몇 일차인지만큼 중요하다 — 다구간 여행에서
      // "3일차"만으로는 어디인지 알 수 없다.
      label: `${day.dayIndex}일차${day.baseCity ? ` · ${day.baseCity.name}` : ""} (${day.date.slice(5)} ${day.weekday})`,
    })),
    { value: ARCHIVE, label: "보관함 · 날짜는 나중에" },
  ];

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = title.trim();
    if (!trimmed) return;
    onSave({
      title: trimmed,
      date: date === ARCHIVE ? null : date,
      startTime: startTime || null,
      memo: memo.trim() || null,
    });
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="일정으로 담기"
      description={placeName ?? undefined}
    >
      <form className="flex flex-col gap-3" onSubmit={submit}>
        <FormField label="일정 제목" htmlFor="placeAddTitle">
          <Input
            id="placeAddTitle"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={100}
          />
        </FormField>

        <FormField label="날짜" labelId="placeAddDate">
          <Select
            ariaLabelledby="placeAddDate"
            value={date}
            onValueChange={setDate}
            options={options}
          />
        </FormField>

        <FormField label="시각 (선택)" htmlFor="placeAddTime">
          <Input
            id="placeAddTime"
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            // 보관함에는 순서가 없다 — 시각을 받아도 쓸 자리가 없다.
            disabled={date === ARCHIVE}
          />
        </FormField>

        <FormField label="메모 (선택)" htmlFor="placeAddMemo">
          <Textarea
            id="placeAddMemo"
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            placeholder="예약 번호, 가는 길 메모"
            maxLength={1000}
            rows={2}
          />
        </FormField>

        <div className="flex justify-end gap-2 pt-1">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button type="submit" disabled={pending || !title.trim()}>
            저장
          </Button>
        </div>
      </form>
    </BottomSheet>
  );
}
