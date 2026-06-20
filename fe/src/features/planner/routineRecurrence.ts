import type { RoutineRecurrence, Weekday } from "./api/routines";

/** 요일 표시 순서(월~일). */
export const WEEKDAYS: Weekday[] = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];

export const WEEKDAY_KO: Record<Weekday, string> = {
  MO: "월",
  TU: "화",
  WE: "수",
  TH: "목",
  FR: "금",
  SA: "토",
  SU: "일",
};

/** 반복 규칙을 한국어 표시 문구로 변환한다(예: "매주 월·수·금", "3일마다", "매월 1·15일"). */
export function recurrenceText(recurrence: RoutineRecurrence): string {
  const interval =
    recurrence.interval && recurrence.interval > 1 ? recurrence.interval : 1;

  switch (recurrence.freq) {
    case "DAILY":
      return interval === 1 ? "매일" : `${interval}일마다`;
    case "WEEKLY": {
      const prefix = interval === 1 ? "매주" : `${interval}주마다`;
      const days = (recurrence.byDay ?? []).map((d) => WEEKDAY_KO[d]).join("·");
      return days ? `${prefix} ${days}` : prefix;
    }
    case "MONTHLY": {
      const prefix = interval === 1 ? "매월" : `${interval}개월마다`;
      const days = recurrence.byMonthDay ?? [];
      return days.length ? `${prefix} ${days.join("·")}일` : prefix;
    }
  }
}

/** 폼 미리보기 라인: "매주 월·수·금 · 2026-06-20부터 ~ 2026-12-31까지". */
export function recurrencePreview(
  recurrence: RoutineRecurrence,
  startDate: string,
): string {
  const parts = [recurrenceText(recurrence)];
  if (startDate) {
    const until = recurrence.until ? ` ~ ${recurrence.until}까지` : "";
    parts.push(`${startDate}부터${until}`);
  }
  return parts.join(" · ");
}
