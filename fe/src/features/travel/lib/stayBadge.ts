import type { BoardDay } from "@/features/travel/api/activities";

/** 배지 한 줄. 어느 숙소를 어떤 꼬리표로 보여줄지는 여기서 정해진다. */
export interface StayBadgeItem {
  stayId: number;
  name: string;
  /** `오늘 체크아웃 11:00`처럼 붙는 꼬리표. 붙일 게 없으면 null. */
  note: string | null;
}

/**
 * 그날의 숙소 배지들 — <b>모두 리스트 위</b>에 선다.
 *
 * <p>숙소를 옮기는 날에는 나가는 곳과 들어가는 곳이 둘 다 나온다. 예전에는 하나를 리스트
 * <b>아래</b>에 뒀는데, 그러면 일정이 두 숙소 사이에 끼어 "이 일정은 어느 숙소에 속하나"처럼
 * 읽혔다. 숙소는 하루의 <b>테두리</b>지 일정 사이의 칸막이가 아니다.
 *
 * <p>순서는 <b>체크아웃이 먼저</b>다(§3.5). 아침에 이 화면을 열었을 때 급한 정보는
 * "오늘 어디서 자나"가 아니라 "몇 시에 나가야 하나"다.
 */
export function stayBadges(day: BoardDay | null): StayBadgeItem[] {
  const badges = [checkoutItem(day), tonightItem(day)].filter(
    (item): item is StayBadgeItem => item !== null,
  );
  // 나가는 곳과 자는 곳이 같으면(= 이어서 묵는 날) 한 줄이면 된다.
  return badges.length === 2 && badges[0].stayId === badges[1].stayId
    ? [badges[0]]
    : badges;
}

function checkoutItem(day: BoardDay | null): StayBadgeItem | null {
  if (!day?.stayCheckout) return null;
  const { stayId, name, checkOutTime } = day.stayCheckout;
  return {
    stayId,
    name,
    note: withTime("오늘 체크아웃", checkOutTime),
  };
}

function tonightItem(day: BoardDay | null): StayBadgeItem | null {
  if (!day?.stayTonight) return null;
  const { stayId, name, checkInTime, isCheckInDay } = day.stayTonight;
  return {
    stayId,
    name,
    // 체크인하는 날은 <b>시각을 몰라도</b> 체크인이라고 말한다 — 체크아웃 쪽과 같은 규칙이다.
    // 한쪽만 꼬리표가 붙으면 나가는 곳인지 들어가는 곳인지를 이름으로 추측하게 된다.
    // 이어서 묵는 날은 붙일 말이 없다(체크인은 지난 일이고 체크아웃은 오늘이 아니다).
    note: isCheckInDay ? withTime("오늘 체크인", checkInTime) : null,
  };
}

function withTime(label: string, time: string | null): string {
  return time ? `${label} ${time}` : label;
}
