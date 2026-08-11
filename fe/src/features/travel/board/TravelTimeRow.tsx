import {
  Car,
  ChevronRight,
  Footprints,
  Navigation,
  TrainFront,
} from "lucide-react";

import type { TravelTime } from "@/features/travel/api/activities";
import { travelTimeLabel } from "@/features/travel/lib/travelTimeLabel";

interface TravelTimeRowProps {
  travelTime: TravelTime;
  /**
   * 탭했을 때. 도시를 넘는 이동이면 이동수단 시트가 아니라 곧바로 지도로 나가야 하므로,
   * 무엇을 열지는 이 행이 아니라 좌표를 아는 쪽(보드)이 정한다.
   */
  onOpen: (travelTime: TravelTime) => void;
  /** 오프라인이면 캐시에서 온 값이고, 다른 수단은 물어볼 수 없다(§4.6). */
  offline: boolean;
}

/**
 * 일정 사이의 이동시간 행(§S-04). 탭하면 이동수단 시트가 열린다.
 *
 * <p>도시를 넘는 이동은 시간 대신 `도시 이동`만 말하고, 탭하면 시트 없이 곧바로 대중교통
 * 길찾기로 나간다(§3.4) — 도시를 넘는 이동에 도보/자동차를 물어볼 이유가 없다.
 */
export function TravelTimeRow({
  travelTime,
  onOpen,
  offline,
}: TravelTimeRowProps) {
  const Icon = travelTime.crossCity
    ? TrainFront
    : travelTime.mode === "WALK"
      ? Footprints
      : Car;
  // 시트가 아니라 밖으로 나간다는 뜻이라 꼬리표도 다르다.
  const Trailing = travelTime.crossCity ? Navigation : ChevronRight;
  // 도시가 바뀌는 지점은 그날 일정에서 가장 큰 사건이라 다른 이동시간 줄보다 조금 세게 읽힌다.
  const tone = travelTime.crossCity
    ? "text-foreground/75 font-medium"
    : "text-muted-foreground";

  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(travelTime)}
        disabled={offline}
        aria-label={`이동시간 ${travelTimeLabel(travelTime)}`}
        className={`${tone} ml-[52px] flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs ${
          offline ? "opacity-60" : "hover:bg-muted"
        }`}
      >
        <Icon className="size-[13px] shrink-0" />
        {travelTimeLabel(travelTime)}
        <Trailing className="size-3 shrink-0" />
      </button>
    </li>
  );
}
