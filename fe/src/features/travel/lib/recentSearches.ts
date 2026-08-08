const STORAGE_KEY = "orino.travel.recentPlaceSearches";
const LIMIT = 5;

/**
 * 최근 검색어(§S-06). 여행마다 따로 남긴다 — 도쿄 여행의 "라멘"이 다음 여행 화면에
 * 떠 있으면 방해만 된다.
 *
 * <p>서버에 두지 않는 이유: 기기 하나에서만 쓰는 편의 기능이고, 저장할 만큼의 값도 아니다.
 * 사파리 프라이빗 모드처럼 저장이 막힌 환경에서도 검색 자체는 되어야 하므로 실패는 삼킨다.
 */
function read(): Record<string, string[]> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object"
      ? (parsed as Record<string, string[]>)
      : {};
  } catch {
    return {};
  }
}

export function getRecentSearches(tripId: number): string[] {
  const entry = read()[String(tripId)];
  return Array.isArray(entry) ? entry.slice(0, LIMIT) : [];
}

/** 이미 있던 검색어는 맨 앞으로 올린다(지우고 다시 넣는다). */
export function addRecentSearch(tripId: number, query: string): string[] {
  const trimmed = query.trim();
  if (!trimmed) return getRecentSearches(tripId);

  const all = read();
  const key = String(tripId);
  const next = [
    trimmed,
    ...(all[key] ?? []).filter((q) => q !== trimmed),
  ].slice(0, LIMIT);

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...all, [key]: next }));
  } catch {
    // 저장 실패는 검색을 막을 이유가 아니다.
  }
  return next;
}

export function clearRecentSearches(tripId: number): void {
  const all = read();
  delete all[String(tripId)];
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
  } catch {
    // 위와 같다.
  }
}
