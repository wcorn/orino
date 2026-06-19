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

import type { EventWriteRequest } from "../../api/events";
import type { PlannerEvent } from "../../api/feed";

interface FormState {
  title: string;
  allDay: boolean;
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
  location: string;
  description: string;
}

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit";
  googleConnected: boolean;
  /** 생성 시 기본 날짜(YYYY-MM-DD) */
  defaultDate: string;
  /** 생성 시 기본 시작 시각("HH:mm"). 주 뷰 슬롯 클릭에서 사용. */
  defaultStartTime?: string;
  /** 편집 대상 일정 */
  event?: PlannerEvent;
  pending?: boolean;
  onSubmit: (values: EventWriteRequest) => void;
  onDelete?: () => void;
}

/** "09:00" → "10:00" (다음 정시, 23시는 23:59). */
function nextHour(time: string): string {
  const hour = Number(time.slice(0, 2));
  return hour >= 23 ? "23:59" : `${String(hour + 1).padStart(2, "0")}:00`;
}

function initialState(
  mode: "create" | "edit",
  defaultDate: string,
  defaultStartTime: string,
  event?: PlannerEvent,
): FormState {
  if (mode === "edit" && event) {
    const allDay = event.allDay;
    return {
      title: event.title ?? "",
      allDay,
      startDate: event.start.slice(0, 10),
      startTime: allDay ? "09:00" : event.start.slice(11, 16) || "09:00",
      endDate: (event.end ?? event.start).slice(0, 10),
      endTime: allDay ? "10:00" : (event.end ?? "").slice(11, 16) || "10:00",
      location: event.location ?? "",
      description: "",
    };
  }
  return {
    title: "",
    allDay: false,
    startDate: defaultDate,
    startTime: defaultStartTime,
    endDate: defaultDate,
    endTime: nextHour(defaultStartTime),
    location: "",
    description: "",
  };
}

export function EventFormDialog({
  open,
  onOpenChange,
  mode,
  googleConnected,
  defaultDate,
  defaultStartTime = "09:00",
  event,
  pending = false,
  onSubmit,
  onDelete,
}: Props) {
  const titleId = useId();
  const [form, setForm] = useState<FormState>(() =>
    initialState(mode, defaultDate, defaultStartTime, event),
  );

  useEffect(() => {
    if (open) {
      setForm(initialState(mode, defaultDate, defaultStartTime, event));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const start = form.allDay
    ? form.startDate
    : `${form.startDate}T${form.startTime}:00`;
  const end = form.allDay ? form.endDate : `${form.endDate}T${form.endTime}:00`;
  const valid =
    form.title.trim().length > 0 &&
    !!form.startDate &&
    !!form.endDate &&
    start <= end;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid || pending) return;
    onSubmit({
      title: form.title.trim(),
      allDay: form.allDay,
      start,
      end,
      location: form.location.trim() || null,
      description: form.description.trim() || null,
    });
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} className="max-w-md">
      <Dialog.Title className="text-base font-semibold">
        {mode === "create" ? "일정 추가" : "일정 편집"}
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
          <FormField label="제목" htmlFor={titleId}>
            <Input
              id={titleId}
              value={form.title}
              onChange={(e) => set("title", e.target.value)}
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

          <FormField label="시작">
            <div className="flex gap-2">
              <Input
                type="date"
                aria-label="시작 날짜"
                value={form.startDate}
                onChange={(e) => set("startDate", e.target.value)}
              />
              {!form.allDay && (
                <Input
                  type="time"
                  aria-label="시작 시간"
                  value={form.startTime}
                  onChange={(e) => set("startTime", e.target.value)}
                />
              )}
            </div>
          </FormField>

          <FormField label="종료">
            <div className="flex gap-2">
              <Input
                type="date"
                aria-label="종료 날짜"
                value={form.endDate}
                onChange={(e) => set("endDate", e.target.value)}
              />
              {!form.allDay && (
                <Input
                  type="time"
                  aria-label="종료 시간"
                  value={form.endTime}
                  onChange={(e) => set("endTime", e.target.value)}
                />
              )}
            </div>
          </FormField>

          <FormField label="장소 (선택)">
            <Input
              value={form.location}
              onChange={(e) => set("location", e.target.value)}
            />
          </FormField>

          <FormField label="메모 (선택)">
            <Textarea
              rows={2}
              value={form.description}
              onChange={(e) => set("description", e.target.value)}
            />
          </FormField>

          <DialogFooter className="mt-1 justify-between">
            {mode === "edit" && onDelete ? (
              <Button
                type="button"
                variant="ghost"
                className="text-destructive"
                disabled={pending}
                onClick={onDelete}
              >
                삭제
              </Button>
            ) : (
              <span />
            )}
            <div className="flex gap-2">
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
            </div>
          </DialogFooter>
        </form>
      )}
    </Modal>
  );
}
