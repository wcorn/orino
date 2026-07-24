/** ISO instant → `datetime-local` 입력값(로컬 시간대, 분 단위). */
export function isoToLocalInput(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  );
}

/** `datetime-local` 입력값(로컬) → ISO instant. 빈 값이면 null. */
export function localInputToIso(local: string): string | null {
  if (!local) return null;
  const d = new Date(local);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

/** 카드/피드 표시용 짧은 로컬 시각(예: "7월 20일 14:30"). */
export function formatMomentTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(d);
}

/** 날짜만(예: "7월 20일"). */
export function formatDay(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(d);
}

/** 흐름 기간(예: "7월 20일 – 7월 22일"). 한쪽만 있으면 그것만, 없으면 빈 문자열. */
export function formatFlowPeriod(
  start: string | null,
  end: string | null,
): string {
  const s = start ? formatDay(start) : "";
  const e = end ? formatDay(end) : "";
  if (s && e) return s === e ? s : `${s} – ${e}`;
  return s || e;
}

/** 같은 로컬 날짜인지(타임라인 날짜 구분용). */
export function localDateKey(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}
