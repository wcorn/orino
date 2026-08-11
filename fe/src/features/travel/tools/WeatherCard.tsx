import { useState } from "react";

import { LoadingText } from "@/components/ui/loading-text";
import type { WeatherForecast } from "@/features/travel/api/tools";
import { iconFor, needsUmbrella } from "@/features/travel/tools/weatherIcon";

interface WeatherCardProps {
  forecast: WeatherForecast | null;
  loading: boolean;
}

function temperature(value: number | null): string {
  return value === null ? "–" : `${value}°`;
}

/**
 * 날씨(§S-08).
 *
 * <p>예보가 비는 것은 오류가 아니다 — Open-Meteo는 <b>16일까지만</b> 준다. 여행이 아직
 * 멀면 아무 날짜도 안 나오고, 그건 계획 단계의 정상 상태다.
 */
export function WeatherCard({ forecast, loading }: WeatherCardProps) {
  const [selected, setSelected] = useState<string | null>(null);
  const daily = forecast?.daily ?? [];
  const activeDate = selected ?? daily[0]?.date ?? null;
  const hourly = activeDate ? (forecast?.hourly[activeDate] ?? []) : [];

  return (
    <section className="border-border bg-card flex flex-col gap-2.5 rounded-xl border p-4">
      <h2 className="text-heading font-medium">날씨</h2>

      {loading ? (
        <LoadingText />
      ) : daily.length === 0 ? (
        // 여행이 16일보다 멀면 여기 머문다. "아직 모른다"이지 고장이 아니다.
        <p className="text-muted-foreground text-[13px]">예보 범위 밖</p>
      ) : (
        <>
          <ul className="flex flex-col">
            {daily.map((day) => {
              const Icon = iconFor(day.icon, day.precipProbability);
              const alert = needsUmbrella(day.precipProbability);
              return (
                <li key={day.date}>
                  <button
                    type="button"
                    onClick={() => setSelected(day.date)}
                    aria-pressed={day.date === activeDate}
                    className={`flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left ${
                      day.date === activeDate ? "bg-muted" : "hover:bg-muted"
                    }`}
                  >
                    <span className="w-[38px] shrink-0 text-[13px] tabular-nums">
                      {day.date.slice(5)}
                    </span>
                    {/* 도시명은 값과 함께 온다(§3.7) — 날짜마다 다른 도시의 예보다. */}
                    {day.cityName && (
                      <span className="text-muted-foreground w-[66px] shrink-0 truncate text-[13px]">
                        {day.cityName}
                      </span>
                    )}
                    <Icon
                      className={`size-4 shrink-0 ${alert ? "text-warning" : "text-muted-foreground"}`}
                    />
                    <span className="flex-1 text-sm tabular-nums">
                      {temperature(day.tempMax)} / {temperature(day.tempMin)}
                    </span>
                    {day.precipProbability !== null && (
                      <span
                        className={`text-xs tabular-nums ${alert ? "text-warning" : "text-muted-foreground"}`}
                      >
                        {day.precipProbability}%
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>

          {hourly.length > 0 && (
            <ul className="flex gap-1.5 overflow-x-auto border-t pt-2.5">
              {hourly.map((hour) => {
                const Icon = iconFor(hour.icon);
                return (
                  <li
                    key={hour.time}
                    className="flex min-w-[52px] flex-col items-center gap-0.5"
                  >
                    <span className="text-muted-foreground text-[11px]">
                      {hour.time}
                    </span>
                    <Icon className="text-muted-foreground size-[15px]" />
                    <span className="text-xs tabular-nums">
                      {temperature(hour.temp)}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}

      {/* Open-Meteo는 출처 표기가 필수다. 데이터가 없어도 남긴다. */}
      <p className="text-muted-foreground text-xs">
        도시별로 따로 조회해요 · Open-Meteo · CC BY 4.0
      </p>
    </section>
  );
}
