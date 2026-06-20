import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useId, useState } from "react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { DialogFooter } from "@/components/ui/dialog-footer";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import { GoogleConnectButton } from "@/features/google/components/GoogleConnectButton";

import type {
  RoutineCreateRequest,
  RoutineRecurrence,
  RoutineSeriesSummary,
  RoutineType,
  Weekday,
} from "../../api/routines";
import {
  recurrencePreview,
  WEEKDAY_KO,
  WEEKDAYS,
} from "../../routineRecurrence";

/** 반복 세그먼트. NDAY는 freq=DAILY + interval=N으로 매핑된다. */
type Segment = "DAILY" | "WEEKLY" | "MONTHLY" | "NDAY";

const SEGMENTS: { value: Segment; label: string }[] = [
  { value: "DAILY", label: "매일" },
  { value: "WEEKLY", label: "주간" },
  { value: "MONTHLY", label: "매월" },
  { value: "NDAY", label: "N일 간격" },
];

interface FormState {
  type: RoutineType;
  title: string;
  allDay: boolean;
  startDate: string;
  startTime: string;
  endTime: string;
  segment: Segment;
  interval: number;
  byDay: Weekday[];
  byMonthDay: number[];
  noEnd: boolean;
  untilDate: string;
  memo: string;
}

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  googleConnected: boolean;
  /** 생성 시 기본 시작일(YYYY-MM-DD) */
  defaultDate: string;
  /** 편집 대상 시리즈. 있으면 편집 모드(폼 prefill, 종류 변경 불가). */
  series?: RoutineSeriesSummary;
  pending?: boolean;
  onSubmit: (values: RoutineCreateRequest) => void;
}

function initialState(type: RoutineType, defaultDate: string): FormState {
  return {
    type,
    title: "",
    allDay: type === "habit",
    startDate: defaultDate,
    startTime: "10:00",
    endTime: "11:00",
    segment: "DAILY",
    interval: 3,
    byDay: [],
    byMonthDay: [],
    noEnd: true,
    untilDate: "",
    memo: "",
  };
}

/** 시리즈 요약(파싱된 recurrence 포함)을 폼 상태로 역매핑한다(편집 prefill). */
function stateFromSeries(series: RoutineSeriesSummary): FormState {
  const r = series.recurrence;
  const interval = r.interval ?? 1;
  let segment: Segment = "DAILY";
  if (r.freq === "WEEKLY") segment = "WEEKLY";
  else if (r.freq === "MONTHLY") segment = "MONTHLY";
  else if (interval > 1) segment = "NDAY";

  return {
    type: series.type,
    title: series.title,
    allDay: series.allDay,
    startDate: series.start.slice(0, 10),
    startTime: series.allDay ? "10:00" : series.start.slice(11, 16) || "10:00",
    endTime: series.end ? series.end.slice(11, 16) || "11:00" : "11:00",
    segment,
    interval: interval > 1 ? interval : 3,
    byDay: r.byDay ?? [],
    byMonthDay: r.byMonthDay ?? [],
    noEnd: !r.until,
    untilDate: r.until ?? "",
    memo: "",
  };
}

function buildRecurrence(form: FormState): RoutineRecurrence {
  const until = form.noEnd ? null : form.untilDate || null;
  switch (form.segment) {
    case "DAILY":
      return { freq: "DAILY", until };
    case "NDAY":
      return { freq: "DAILY", interval: form.interval, until };
    case "WEEKLY":
      return { freq: "WEEKLY", byDay: form.byDay, until };
    case "MONTHLY":
      return { freq: "MONTHLY", byMonthDay: form.byMonthDay, until };
  }
}

export function RoutineFormDialog({
  open,
  onOpenChange,
  googleConnected,
  defaultDate,
  series,
  pending = false,
  onSubmit,
}: Props) {
  const editing = !!series;
  const titleId = useId();
  const [form, setForm] = useState<FormState>(() =>
    series ? stateFromSeries(series) : initialState("habit", defaultDate),
  );
  const [monthDayInput, setMonthDayInput] = useState("");

  useEffect(() => {
    if (open) {
      setForm(
        series ? stateFromSeries(series) : initialState("habit", defaultDate),
      );
      setMonthDayInput("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const setType = (type: RoutineType) =>
    setForm((prev) => ({ ...prev, type, allDay: type === "habit" }));

  const toggleWeekday = (day: Weekday) =>
    setForm((prev) => ({
      ...prev,
      byDay: prev.byDay.includes(day)
        ? prev.byDay.filter((d) => d !== day)
        : [...prev.byDay, day].sort(
            (a, b) => WEEKDAYS.indexOf(a) - WEEKDAYS.indexOf(b),
          ),
    }));

  const addMonthDay = (day: number) => {
    if (!Number.isInteger(day) || day < 1 || day > 31) return;
    setForm((prev) =>
      prev.byMonthDay.includes(day)
        ? prev
        : {
            ...prev,
            byMonthDay: [...prev.byMonthDay, day].sort((a, b) => a - b),
          },
    );
    setMonthDayInput("");
  };

  const removeMonthDay = (day: number) =>
    set(
      "byMonthDay",
      form.byMonthDay.filter((d) => d !== day),
    );

  const recurrence = buildRecurrence(form);
  const start = form.allDay
    ? form.startDate
    : `${form.startDate}T${form.startTime}:00`;
  const end = form.allDay
    ? form.startDate
    : `${form.startDate}T${form.endTime}:00`;

  const recurrenceValid =
    (form.segment !== "WEEKLY" || form.byDay.length > 0) &&
    (form.segment !== "MONTHLY" || form.byMonthDay.length > 0) &&
    (form.segment !== "NDAY" || form.interval >= 1);
  const valid =
    form.title.trim().length > 0 &&
    !!form.startDate &&
    recurrenceValid &&
    (form.noEnd || (!!form.untilDate && form.untilDate >= form.startDate)) &&
    (form.allDay || form.endTime > form.startTime);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid || pending) return;
    onSubmit({
      type: form.type,
      title: form.title.trim(),
      allDay: form.allDay,
      start,
      end,
      recurrence,
      memo: form.memo.trim() || null,
      color: null,
    });
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} className="max-w-md">
      <Dialog.Title className="text-base font-semibold">
        {editing ? "루틴 편집" : "새 루틴"}
      </Dialog.Title>

      {!googleConnected ? (
        <div className="mt-4 flex flex-col gap-4">
          <p className="text-muted-foreground text-sm">
            Google 연결이 필요합니다.
          </p>
          <DialogFooter className="mt-0">
            <Dialog.Close
              render={
                <Button variant="ghost" type="button">
                  닫기
                </Button>
              }
            />
            <GoogleConnectButton />
          </DialogFooter>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-3">
          <FormField label="종류">
            <div className="flex gap-2">
              {(
                [
                  { value: "habit", label: "습관(체크형)" },
                  { value: "schedule", label: "고정 일정" },
                ] as const
              ).map((opt) => (
                <Button
                  key={opt.value}
                  type="button"
                  variant={form.type === opt.value ? "default" : "outline"}
                  aria-pressed={form.type === opt.value}
                  disabled={editing}
                  onClick={() => setType(opt.value)}
                >
                  {opt.label}
                </Button>
              ))}
            </div>
          </FormField>

          <FormField label="제목" htmlFor={titleId}>
            <Input
              id={titleId}
              value={form.title}
              onChange={(e) => set("title", e.target.value)}
              placeholder="운동하기"
              autoFocus
            />
          </FormField>

          <label className="flex items-center gap-2 text-sm">
            <Checkbox
              checked={form.allDay}
              onChange={(e) => set("allDay", e.target.checked)}
            />
            종일
          </label>

          {!form.allDay && (
            <FormField label="시간">
              <div className="flex items-center gap-2">
                <Input
                  type="time"
                  aria-label="시작 시간"
                  value={form.startTime}
                  onChange={(e) => set("startTime", e.target.value)}
                />
                <span className="text-muted-foreground">~</span>
                <Input
                  type="time"
                  aria-label="종료 시간"
                  value={form.endTime}
                  onChange={(e) => set("endTime", e.target.value)}
                />
              </div>
            </FormField>
          )}

          <FormField label="반복">
            <div className="flex flex-wrap gap-2">
              {SEGMENTS.map((seg) => (
                <Button
                  key={seg.value}
                  type="button"
                  size="sm"
                  variant={form.segment === seg.value ? "default" : "outline"}
                  aria-pressed={form.segment === seg.value}
                  onClick={() => set("segment", seg.value)}
                >
                  {seg.label}
                </Button>
              ))}
            </div>
          </FormField>

          {form.segment === "WEEKLY" && (
            <FormField label="요일">
              <div className="flex gap-1">
                {WEEKDAYS.map((day) => (
                  <Button
                    key={day}
                    type="button"
                    size="sm"
                    className="flex-1 px-0"
                    variant={form.byDay.includes(day) ? "default" : "outline"}
                    aria-pressed={form.byDay.includes(day)}
                    aria-label={WEEKDAY_KO[day]}
                    onClick={() => toggleWeekday(day)}
                  >
                    {WEEKDAY_KO[day]}
                  </Button>
                ))}
              </div>
            </FormField>
          )}

          {form.segment === "MONTHLY" && (
            <FormField label="일자">
              <div className="flex flex-col gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Input
                    type="number"
                    min={1}
                    max={31}
                    aria-label="일자 입력"
                    className="w-20"
                    value={monthDayInput}
                    onChange={(e) => setMonthDayInput(e.target.value)}
                  />
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => addMonthDay(Number(monthDayInput))}
                  >
                    추가
                  </Button>
                </div>
                {form.byMonthDay.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {form.byMonthDay.map((day) => (
                      <Button
                        key={day}
                        type="button"
                        size="sm"
                        variant="secondary"
                        aria-label={`${day}일 제거`}
                        onClick={() => removeMonthDay(day)}
                      >
                        {day}일 ✕
                      </Button>
                    ))}
                  </div>
                )}
              </div>
            </FormField>
          )}

          {form.segment === "NDAY" && (
            <FormField label="간격">
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  min={1}
                  aria-label="간격 일수"
                  className="w-20"
                  value={form.interval}
                  onChange={(e) => set("interval", Number(e.target.value))}
                />
                <span className="text-muted-foreground text-sm">일마다</span>
              </div>
            </FormField>
          )}

          <FormField label="시작">
            <Input
              type="date"
              aria-label="시작 날짜"
              value={form.startDate}
              onChange={(e) => set("startDate", e.target.value)}
            />
          </FormField>

          <FormField label="종료">
            <div className="flex items-center gap-2">
              <label className="flex items-center gap-2 text-sm">
                <Checkbox
                  checked={form.noEnd}
                  onChange={(e) => set("noEnd", e.target.checked)}
                />
                없음
              </label>
              {!form.noEnd && (
                <Input
                  type="date"
                  aria-label="종료 날짜"
                  value={form.untilDate}
                  onChange={(e) => set("untilDate", e.target.value)}
                />
              )}
            </div>
          </FormField>

          <FormField label="메모 (선택)">
            <Textarea
              rows={2}
              value={form.memo}
              onChange={(e) => set("memo", e.target.value)}
            />
          </FormField>

          <p
            className="text-muted-foreground text-sm"
            data-testid="routine-preview"
          >
            {recurrencePreview(recurrence, form.startDate)}
          </p>

          <DialogFooter className="mt-1">
            <Dialog.Close
              render={
                <Button variant="ghost" type="button" disabled={pending}>
                  취소
                </Button>
              }
            />
            <Button type="submit" disabled={!valid || pending}>
              {pending ? "저장 중..." : "저장"}
            </Button>
          </DialogFooter>
        </form>
      )}
    </Modal>
  );
}
