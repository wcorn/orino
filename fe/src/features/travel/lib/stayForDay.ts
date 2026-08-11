/**
 * 숙소 날짜 판정. **기준 도시와 무관하다** — 닛코 당일치기 날의 기준 도시는 닛코지만
 * 자는 곳은 도쿄일 수 있다.
 *
 * 판정은 반열린 구간 `[checkIn, checkOut)`으로 한다. **체크아웃일 밤은 이미 다른 곳에서
 * 잔다** — 이 한 줄이 규칙의 전부이고, 화면마다 다시 쓰면 한 곳에서 경계가 어긋난다.
 *
 * ```
 * stayTonight(day)  = checkInDate <= day <  checkOutDate   오늘 밤 자는 곳
 * stayCheckout(day) = checkOutDate == day                  오늘 체크아웃하는 곳
 * ```
 *
 * 보드 응답의 `stayTonight`·`stayCheckout`은 서버가 **같은 규칙으로** 채워 보낸다. 여기 규칙이
 * 따로 있는 이유는 등록 폼의 겹침 미리보기처럼 **서버에 묻기 전에 답해야 하는 자리**가 있어서다.
 */

import { formatShortDate } from "./tripStatus";

/** 판정에 필요한 최소한. 날짜는 `"YYYY-MM-DD"` — 사전순 비교가 곧 날짜 비교다. */
export interface StayPeriod {
  checkInDate: string;
  checkOutDate: string;
}

/** 그날 밤 여기서 자는가. 체크아웃일은 포함하지 않는다. */
export function coversNight(stay: StayPeriod, date: string): boolean {
  return stay.checkInDate <= date && date < stay.checkOutDate;
}

/** 그날 여기서 체크아웃하는가. */
export function isCheckOutOn(stay: StayPeriod, date: string): boolean {
  return stay.checkOutDate === date;
}

/**
 * 두 숙소의 묵는 밤이 겹치는가. `10.24–10.27` 다음의 `10.27–10.29`는 **겹침이 아니다**
 * (이동일).
 */
export function overlaps(a: StayPeriod, b: StayPeriod): boolean {
  return a.checkInDate < b.checkOutDate && b.checkInDate < a.checkOutDate;
}

/**
 * 겹침 안내 문구. **겹친 숙소의 이름과 기간을 그대로** 보여준다 — 무엇을 줄여야 하는지가
 * 곧 답이다. "이미 숙소가 잡힌 기간입니다"만으로는 사용자가 어느 숙소를 손봐야 할지 모른다.
 *
 * <p>클라이언트가 미리 잡은 겹침과 서버가 409로 돌려준 겹침이 같은 문장을 쓴다 — 같은
 * 사실을 두 가지로 말하면 사용자는 다른 문제라고 읽는다.
 */
export function overlapMessage(
  conflict: StayPeriod & { name: string },
): string {
  const period = `${formatShortDate(conflict.checkInDate)}–${formatShortDate(conflict.checkOutDate)}`;
  return `${conflict.name}(${period})와 기간이 겹쳐요. 기존 숙소 기간을 먼저 줄여주세요.`;
}

/** 그날 밤 자는 곳. 겹치는 숙소는 저장되지 않으므로 답은 하나뿐이다. */
export function stayTonight<T extends StayPeriod>(
  stays: T[],
  date: string,
): T | null {
  return stays.find((stay) => coversNight(stay, date)) ?? null;
}

/** 그날 체크아웃하는 곳. */
export function stayCheckout<T extends StayPeriod>(
  stays: T[],
  date: string,
): T | null {
  return stays.find((stay) => isCheckOutOn(stay, date)) ?? null;
}
