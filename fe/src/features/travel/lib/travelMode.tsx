import {
  Bike,
  Bus,
  Car,
  Footprints,
  Plane,
  Route,
  Ship,
  Train,
  TrainFront,
} from "lucide-react";

import type { Move, TravelMode } from "@/features/travel/api/activities";

/**
 * 이동수단 분류 하나. 아이콘과 라벨이 여기 한 곳에만 있다 — 행·시트·요약이 저마다 매핑을
 * 들면 한 화면만 조용히 다른 아이콘을 그린다.
 */
interface ModeMeta {
  mode: TravelMode;
  label: string;
  Icon: typeof Footprints;
}

/**
 * 화면이 고를 수 있는 이동수단(#1208).
 *
 * <p>**나라 고유명이 없다.** `신칸센`을 목록에 박으면 일본 밖에서 쓸 수 없고, 다음엔 `TGV`·
 * `KTX`를 넣어 달라는 요청이 이어진다. 분류는 범용으로 두고 구체적인 이름은 `name`에 적는다
 * — `기차` + `노조미 21호`.
 *
 * <p>`기타`가 있는 이유도 같다. 케이블카·툭툭 어느 것이든 적을 자리가 있어야 한다 —
 * 목록에 없다는 이유로 이동을 기록하지 못하면 계획 전체에 구멍이 난다.
 *
 * <p>순서는 가까운 이동부터다. 하루 안에서 가장 자주 고르는 것이 위에 온다.
 */
export const TRAVEL_MODES: ModeMeta[] = [
  { mode: "WALK", label: "도보", Icon: Footprints },
  { mode: "BIKE", label: "자전거", Icon: Bike },
  { mode: "SUBWAY", label: "지하철 · 전철", Icon: TrainFront },
  { mode: "BUS", label: "버스", Icon: Bus },
  { mode: "CAR", label: "자동차 · 택시", Icon: Car },
  { mode: "TRAIN", label: "기차", Icon: Train },
  { mode: "FLIGHT", label: "비행기", Icon: Plane },
  { mode: "FERRY", label: "배 · 페리", Icon: Ship },
  { mode: "OTHER", label: "기타", Icon: Route },
];

export function modeMeta(mode: TravelMode): ModeMeta {
  // 서버가 우리가 모르는 값을 주는 일은 없지만, 그때 화면이 빈칸이 되는 것보다는
  // `기타`로 그려지는 편이 낫다.
  return TRAVEL_MODES.find((m) => m.mode === mode) ?? TRAVEL_MODES[8];
}

/**
 * 이동 한 건을 사람이 읽는 한 줄로.
 *
 * <p>**적어 둔 이름이 있으면 그것이 앞에 온다** — 현지에서 필요한 것은 `기차`가 아니라
 * `나리타 익스프레스 3호`다. 분류는 아이콘이 이미 말하고 있다.
 *
 * <p>아무것도 적지 않았으면 시간을 지어내지 않고 **적으라고 말한다.** 빈 값을 `0분`으로
 * 답하면 화면이 "바로 옆"이라고 읽는다.
 */
export function moveLabel(move: Move): string {
  if (move.mode === null) {
    return "이동 추가";
  }
  const what = move.name?.trim() || modeMeta(move.mode).label;
  return move.durationMinutes === null
    ? what
    : `${what} · ${move.durationMinutes}분`;
}
