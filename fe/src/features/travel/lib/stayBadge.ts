import type { BoardDay } from "@/features/travel/api/activities";

/** 배지 한 줄. 어느 숙소를 어떤 꼬리표로 보여줄지는 여기서 정해진다. */
export interface StayBadgeItem {
  stayId: number;
  name: string;
  /** `오늘 체크아웃 11:00`처럼 붙는 꼬리표. 붙일 게 없으면 null. */
  note: string | null;
}

/**
 * 리스트 **위** 배지 — <b>체크아웃이 먼저다</b>(§3.5).
 *
 * <p>아침에 이 화면을 열었을 때 급한 정보는 "오늘 어디서 자나"가 아니라 "몇 시에 나가야
 * 하나"다. 체크아웃하는 날에 오늘 밤 숙소를 위에 띄우면, 정작 시간에 쫓기는 정보가 아래로
 * 밀린다.
 */
export function badgeAboveList(day: BoardDay | null): StayBadgeItem | null {
  if (day?.stayCheckout) {
    const { stayId, name, checkOutTime } = day.stayCheckout;
    return {
      stayId,
      name,
      note: checkOutTime ? `오늘 체크아웃 ${checkOutTime}` : "오늘 체크아웃",
    };
  }
  return tonightItem(day);
}

/**
 * 리스트 **아래** 배지 — 위 배지와 <b>다를 때만</b>(= 숙소를 옮기는 날).
 *
 * <p>같으면 같은 숙소를 한 화면에 두 번 쓰는 것이라 정보가 아니라 소음이다. 다를 때만
 * 그리면 그 자리에 배지가 있다는 것 자체가 "오늘 숙소를 옮긴다"는 뜻이 된다.
 */
export function badgeBelowList(day: BoardDay | null): StayBadgeItem | null {
  const tonight = tonightItem(day);
  if (tonight === null) return null;
  return badgeAboveList(day)?.stayId === tonight.stayId ? null : tonight;
}

function tonightItem(day: BoardDay | null): StayBadgeItem | null {
  if (!day?.stayTonight) return null;
  const { stayId, name, checkInTime, isCheckInDay } = day.stayTonight;
  return {
    stayId,
    name,
    // 체크인하는 날에만 시각을 붙인다 — 이어서 묵는 날에 체크인 시각은 지난 정보다.
    note: isCheckInDay && checkInTime ? `오늘 체크인 ${checkInTime}` : null,
  };
}
