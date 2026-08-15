import { ArrowRight } from "lucide-react";

import type { BoardDay } from "@/features/travel/api/activities";
import type { DailyWeather } from "@/features/travel/api/tools";
import { iconFor, needsUmbrella } from "@/features/travel/tools/weatherIcon";
import { cn } from "@/lib/utils";

interface CityMoveLineProps {
  /** 보고 있는 날짜. 보관함이면 null이다. */
  day: BoardDay | null | undefined;
}

/**
 * 도시가 바뀌는 날의 한 줄 — `오사카 18°/11° → 교토 16°/9°`.
 *
 * <p>그 하루는 두 도시에 속한다(D-25). 날짜 탭은 도착 도시의 날씨만 보여주는데, 아침에 뭘
 * 입을지는 <b>오전을 보낼 도시</b>가 정한다 — 오사카에 비가 오면 교토가 맑아도 우산을 든다.
 *
 * <p>이동일이 아니면 아무것도 그리지 않는다. 안 바뀌는 날에 빈 자리를 남기지 않는다.
 */
export function CityMoveLine({ day }: CityMoveLineProps) {
  const from = day?.arrivingFrom;
  if (!day || !from || !day.baseCity) {
    return null;
  }
  return (
    <p
      className="bg-muted flex items-center gap-2 rounded-lg px-3 py-2 text-[13px]"
      aria-label={`${from.name}에서 ${day.baseCity.name}로 이동하는 날`}
    >
      <CityWeather name={from.name} weather={day.arrivingFromWeather} />
      <ArrowRight
        className="text-muted-foreground size-3.5 shrink-0"
        aria-hidden="true"
      />
      <CityWeather name={day.baseCity.name} weather={day.weather} />
    </p>
  );
}

/** 도시명 + 그 도시의 그날 기온. 예보 범위(16일) 밖이면 이름만 남는다. */
function CityWeather({
  name,
  weather,
}: {
  name: string;
  weather?: DailyWeather | null;
}) {
  const Glyph = weather
    ? iconFor(weather.icon, weather.precipProbability)
    : null;
  const alert = weather ? needsUmbrella(weather.precipProbability) : false;
  return (
    <span className="flex min-w-0 items-center gap-1">
      <span className="truncate">{name}</span>
      {weather && Glyph && (
        <span
          className={cn(
            "flex shrink-0 items-center gap-0.5 text-xs tabular-nums",
            alert ? "text-warning" : "text-muted-foreground",
          )}
        >
          <Glyph className="size-3" />
          {weather.tempMax ?? "–"}°/{weather.tempMin ?? "–"}°
        </span>
      )}
    </span>
  );
}
