import type { SidebarTripSummary } from "../api/travel";

const KEY = "travel.lastTripId";

/**
 * 마지막으로 본 여행. <b>진실이 아니라 폴백이다</b>(D-37).
 *
 * <p>선택된 여행의 진실은 URL이다. 이 값은 <b>여행 id가 없는 진입</b>(`/travel/prep` ·
 * 사이드바가 여행을 못 정한 순간)에서만 읽는다 — 진실로 삼으면 링크를 공유했을 때 받은
 * 사람의 화면이 URL과 다른 여행을 열고, 뒤로 가기도 주소와 어긋난다.
 *
 * <p>저장된 id는 <b>더 이상 없을 수 있다</b> — 여행을 지웠거나, 다른 기기에서 만든 값이거나,
 * 시크릿 창이거나. 그래서 읽는 쪽이 항상 요약의 `trips[]`로 걸러 낸다. 죽은 id로 화면을
 * 열면 「여행을 못 찾았습니다」가 사용자 잘못처럼 보인다.
 */
export function rememberTrip(tripId: number): void {
  try {
    localStorage.setItem(KEY, String(tripId));
  } catch {
    // 저장이 막힌 환경(시크릿 창·차단 설정)에서도 화면은 그대로 동작해야 한다.
    // 기억하지 못할 뿐이고, 그건 폴백 화면이 받아 준다.
  }
}

/**
 * 저장된 여행 중 <b>지금도 고를 수 있는</b> 것. 없으면 null이다.
 *
 * @param trips 요약의 진행 중·예정 여행. 여기 없는 id는 조용히 버린다
 */
export function readLastTrip(trips: SidebarTripSummary[]): number | null {
  let stored: string | null = null;
  try {
    stored = localStorage.getItem(KEY);
  } catch {
    return null;
  }
  if (stored === null) return null;
  const tripId = Number(stored);
  return trips.some((trip) => trip.id === tripId) ? tripId : null;
}
