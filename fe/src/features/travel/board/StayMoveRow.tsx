import { Car, Footprints, Hotel, TrainFront } from "lucide-react";

import type { StayMove } from "@/features/travel/api/activities";

interface StayMoveRowProps {
  stayMove: StayMove;
}

/**
 * 리스트 맨 아래 붙는 숙소 이동(§2.5) — 그날 마지막 일정에서 오늘 밤 자는 곳까지.
 *
 * <p>도시를 넘으면 서버가 계산하지 않는다(§3.4) — 시간 없이 `숙소로 이동`만 말한다.
 * 좌표를 모르는 숙소도 마찬가지다. 이동이 성립하지 않는 것을 `0분`으로 답하면 화면이
 * "바로 옆"이라고 읽는다.
 *
 * <p>탭할 것이 없다 — 목적지는 배지가 열어 주고, 길찾기는 그 안에 있다. 여기서 또 시트를
 * 열면 같은 숙소로 가는 문이 두 개가 된다.
 */
export function StayMoveRow({ stayMove }: StayMoveRowProps) {
  const { sameCity, mode, durationMinutes } = stayMove;
  const computed = durationMinutes !== null;
  const Icon = computed
    ? mode === "WALK"
      ? Footprints
      : Car
    : sameCity
      ? Hotel
      : TrainFront;
  const label = computed ? `숙소까지 ${durationMinutes}분` : "숙소로 이동";

  return (
    <li className="border-t pt-2.5">
      <p className="text-muted-foreground ml-[52px] flex items-center gap-1.5 px-2 text-xs">
        <Icon className="size-[13px] shrink-0" />
        {label}
      </p>
    </li>
  );
}
