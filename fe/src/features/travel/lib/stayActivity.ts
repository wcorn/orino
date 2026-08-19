import type { Stay } from "@/features/travel/api/stays";

/**
 * 숙소를 <b>보고 있는 날짜</b>의 일정으로 담을 때 붙일 이름.
 *
 * <p>그 날짜가 숙소에게 무슨 날인지를 이름이 말한다 — 체크인하는 날이면 `체크인`,
 * 나가는 날이면 `체크아웃`, 그 사이의 묵는 날이면 숙소 이름만. 묵는 날에 `체크인`이 붙으면
 * 이미 지난 일을 오늘 할 일처럼 적게 된다.
 */
export function stayActivityTitle(stay: Stay, date: string): string {
  if (date === stay.checkInDate) return `${stay.name} 체크인`;
  if (date === stay.checkOutDate) return `${stay.name} 체크아웃`;
  return stay.name;
}

/**
 * 그 일정에 넣을 시각. 체크인·체크아웃 날에만 아는 시각이 있고, 묵는 날에는 없다.
 * 모르면 비운다 — 지어낸 시각은 일정 순서를 틀리게 만든다.
 */
export function stayActivityTime(stay: Stay, date: string): string | null {
  if (date === stay.checkInDate) return stay.checkInTime;
  if (date === stay.checkOutDate) return stay.checkOutTime;
  return null;
}
