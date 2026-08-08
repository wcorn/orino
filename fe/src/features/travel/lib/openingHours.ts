/**
 * Google이 준 영업시간 원본에서 "오늘" 줄만 뽑는다.
 *
 * <p>서버는 구조를 해석하지 않고 원본 JSON을 그대로 넘긴다(§4.7) — 구글이 형태를 바꿔도
 * 서버·DB를 손대지 않기 위해서다. 대신 해석은 여기서 하고, 못 읽으면 조용히 비운다.
 *
 * <p>일곱 줄을 다 보여주지 않는다. 현지에서 알고 싶은 건 "지금 열었나"뿐이고,
 * 나머지 요일은 화면만 차지한다.
 */
export function todayOpeningHours(
  raw: string | null,
  now: Date = new Date(),
): string | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    const descriptions = (parsed as { weekdayDescriptions?: unknown })
      ?.weekdayDescriptions;
    if (!Array.isArray(descriptions) || descriptions.length < 7) return null;

    // Google은 월요일부터 준다. Date.getDay()는 일요일이 0이다.
    const index = (now.getDay() + 6) % 7;
    const line = descriptions[index];
    return typeof line === "string" ? line : null;
  } catch {
    return null;
  }
}
