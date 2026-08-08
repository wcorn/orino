/**
 * 여행지 현지 시각 표시(§4.1).
 *
 * <p>기기와 여행의 오프셋이 같으면 <b>보여줄 이유가 없다</b> — 서울에서 도쿄 여행을 보면
 * 시계가 똑같아서 줄만 차지한다. 실제로 도움이 되는 건 오프셋이 다를 때뿐이다.
 */
export function sameOffset(timezone: string, at: Date = new Date()): boolean {
  return offsetMinutes(timezone, at) === -at.getTimezoneOffset();
}

/** 그 타임존의 UTC 오프셋(분). `Intl`로 계산해 DST까지 따라간다. */
function offsetMinutes(timezone: string, at: Date): number {
  // 같은 순간을 그 타임존의 벽시계로 읽어 UTC와의 차이를 잰다.
  const asUtc = new Date(at.toLocaleString("en-US", { timeZone: "UTC" }));
  const asZone = new Date(at.toLocaleString("en-US", { timeZone: timezone }));
  return Math.round((asZone.getTime() - asUtc.getTime()) / 60_000);
}

/** `09:42` — 여행 타임존의 현재 시각. */
export function localTime(timezone: string, at: Date = new Date()): string {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(at);
}
