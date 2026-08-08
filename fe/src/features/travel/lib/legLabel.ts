import type { Leg } from "@/features/travel/api/activities";

/**
 * 이동시간을 사람이 읽는 값으로.
 *
 * <p>계산이 실패하면(`fallback`) 시간을 지어내지 않고 <b>거리만</b> 보여준다 — 거리만 알아도
 * "걸어갈 만한가"는 판단이 서지만, 틀린 분 수는 계획을 망친다.
 */
export function legLabel(leg: Leg): string {
  if (leg.fallback || leg.durationMinutes === null) {
    return `약 ${(leg.distanceM / 1000).toFixed(1)}km`;
  }
  return `${leg.durationMinutes}분`;
}
