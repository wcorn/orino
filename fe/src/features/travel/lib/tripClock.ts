/**
 * 여행 타임존의 <b>벽시계 시각</b>을 다루는 최소 도구.
 *
 * <p>여기 있는 값은 전부 `"HH:mm"` 문자열이고, <b>절대 `Date`로 파싱하지 않는다.</b>
 * `new Date("09:00")`이나 `new Date(\`${date}T${time}\`)`은 기기 타임존으로 해석돼,
 * 서울에서 도쿄 일정을 열면 09:00이 08:00으로 보이는 식으로 어긋난다. 일정 시각은
 * "그 도시에서 시계가 가리키는 값"이지 특정 순간이 아니다.
 *
 * <p>절대시각이 필요한 곳은 알림 예약뿐이고, 그 환산은 서버가 `trip.timezone`으로 한다.
 */

/** `"HH:mm"` 형태인지. 초가 붙은 값(`"09:00:00"`)도 받아들여 잘라 쓴다. */
const TIME_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)(:[0-5]\d)?$/;

/**
 * 입력값을 저장 가능한 벽시계 시각으로 정리한다.
 * 비었거나 형식이 아니면 `null` — 시각 없는 일정은 정상이다(§1.1).
 */
export function toWallClockTime(
  value: string | null | undefined,
): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  const match = TIME_PATTERN.exec(trimmed);
  if (!match) return null;
  // `input type="time"`은 초를 붙여 줄 때가 있다. 저장 형식은 항상 HH:mm이다.
  return `${match[1]}:${match[2]}`;
}

/**
 * 서버 값을 `input type="time"`에 넣을 문자열로. 없으면 빈 문자열(컨트롤드 입력 유지).
 */
export function toTimeInputValue(value: string | null | undefined): string {
  return toWallClockTime(value) ?? "";
}
