import { Cloud, CloudRain, CloudSnow, Sun, Umbrella } from "lucide-react";

import type { WeatherIcon } from "@/features/travel/api/tools";

const ICONS = {
  CLEAR: Sun,
  CLOUD: Cloud,
  RAIN: CloudRain,
  SNOW: CloudSnow,
} as const;

/** 이 확률 이상이면 우산을 챙긴다(§1.8). 이 카드를 보는 이유가 사실상 이것이다. */
export const RAIN_ALERT_THRESHOLD = 60;

export function needsUmbrella(precipProbability: number | null): boolean {
  return (
    precipProbability !== null && precipProbability >= RAIN_ALERT_THRESHOLD
  );
}

/**
 * 아이콘 고르기.
 *
 * <p>강수확률이 높으면 날씨 아이콘 대신 <b>우산</b>을 보여준다 — "구름"과 "우산 필요"는
 * 다른 정보고, 챙겨야 하는 쪽이 눈에 띄어야 한다.
 */
export function iconFor(
  icon: WeatherIcon,
  precipProbability: number | null = null,
) {
  return needsUmbrella(precipProbability) ? Umbrella : ICONS[icon];
}
