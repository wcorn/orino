import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Languages } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { fetchExchangeRate, fetchWeather } from "@/features/travel/api/tools";
import { useBoard } from "@/features/travel/hooks/useBoard";
import { useTravelSummary } from "@/features/travel/hooks/useTravelSummary";
import { useTrip } from "@/features/travel/hooks/useTrip";
import { cityOn, tripCities } from "@/features/travel/lib/baseCity";
import { travelKeys } from "@/features/travel/queryKeys";
import {
  type Currency,
  defaultCurrency,
} from "@/features/travel/tools/currencies";
import { ExchangeRateCard } from "@/features/travel/tools/ExchangeRateCard";
import { translateUrl } from "@/features/travel/tools/translateLink";
import { WeatherCard } from "@/features/travel/tools/WeatherCard";
import { useOnline } from "@/shared/lib/useOnline";

/** 원화 기준으로 환산한다 — 사용자가 아는 돈이 원화다. */
const HOME_CURRENCY = "KRW";

/**
 * S-08 도구 — 환율 · 날씨 · 번역.
 *
 * <p>세 카드가 <b>여행에 매달려</b> 있다. 환율은 여행 통화, 날씨는 여행 좌표·기간,
 * 번역은 여행 타임존에서 나온다. 그래서 어느 여행인지가 먼저 정해져야 한다 —
 * 보드에서 들어오면 그 여행, 아니면 진행 중이거나 다음 예정 여행이다.
 */
export function TravelToolsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const online = useOnline();

  const requestedTripId = searchParams.get("tripId");
  const { data: summary, isPending: loadingSummary } = useTravelSummary();
  const tripId = requestedTripId
    ? Number(requestedTripId)
    : (summary?.ongoing?.id ?? summary?.next?.id ?? null);

  const { data: trip, isPending: loadingTrip } = useTrip(tripId);
  /**
   * 그날의 보드 — <b>오늘 어느 도시에 있는지</b>가 여기에만 있다. 날짜를 지정하지 않으면
   * 서버가 진행 중일 때 오늘을, 아니면 1일차를 고른다(§4.1).
   *
   * <p>여행 상세의 {@code trip.currency}·{@code trip.timezone}으로는 안 된다 — 서버가
   * <b>첫날</b> 기준 도시에서 파생해 채워 준 값이라, 교토에 있는 날에도 오사카의 통화·언어를
   * 말한다. 같은 엔·같은 나라라 눈에 안 띌 뿐이고 국가를 넘으면 곧바로 틀린다.
   */
  const { data: board } = useBoard(
    tripId ?? 0,
    {},
    { enabled: tripId !== null },
  );
  /** 오늘(또는 첫날) 기준 도시. 통화·언어·칩이 전부 여기서 나온다. */
  const todayCity = cityOn(board?.days ?? [], board?.selectedDate ?? "");

  const { data: weather, isPending: loadingWeather } = useQuery({
    queryKey: travelKeys.weather(tripId ?? 0),
    queryFn: () => fetchWeather(tripId!),
    enabled: tripId !== null,
    // 서버가 6시간 캐시한다. 화면이 더 자주 물어볼 이유가 없다.
    staleTime: 30 * 60 * 1000,
  });

  // 오늘 도시의 통화가 기본이되 바꿀 수 있다 — 경유지에서 다른 돈을 쓰기도 한다.
  const [currency, setCurrency] = useState<Currency>(() =>
    defaultCurrency(todayCity?.currency),
  );
  // 보드가 늦게 도착하거나(로딩) 다른 여행으로 바뀌면 그 도시 통화로 다시 맞춘다.
  useEffect(() => {
    setCurrency(defaultCurrency(todayCity?.currency));
  }, [todayCity?.currency]);

  const { data: rate, isPending: loadingRate } = useQuery({
    queryKey: travelKeys.fx(currency, HOME_CURRENCY),
    queryFn: () => fetchExchangeRate(currency, HOME_CURRENCY),
    enabled: trip !== undefined,
    staleTime: 60 * 60 * 1000,
  });

  if (loadingSummary || (tripId !== null && loadingTrip)) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3 px-4 pt-3">
      <header className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="뒤로"
          onClick={() => navigate(-1)}
        >
          <ArrowLeft className="size-4" />
        </Button>
        <h1 className="text-heading font-semibold">도구</h1>
      </header>

      {trip === undefined ? (
        <p className="text-muted-foreground text-sm">
          여행을 만들면 환율과 날씨를 볼 수 있어요.
        </p>
      ) : (
        <>
          <ExchangeRateCard
            rate={rate ?? null}
            loading={loadingRate}
            online={online}
            currency={currency}
            onCurrencyChange={setCurrency}
            cityName={todayCity?.name}
            tripCurrencies={tripCities(board?.days ?? []).map(
              (c) => c.currency,
            )}
          />
          <WeatherCard forecast={weather ?? null} loading={loadingWeather} />

          <section className="border-border bg-card flex flex-col gap-2.5 rounded-xl border p-4">
            <h2 className="text-heading font-medium">번역</h2>
            <p className="text-muted-foreground text-[13px]">
              한국어 → 현지 언어로 구글 번역을 엽니다. 앱이 없으면 웹으로
              열려요.
            </p>
            <Button
              variant="outline"
              onClick={() =>
                window.open(
                  // 목적 언어는 기준 도시 <b>국가</b>를 따라간다(§3.7).
                  translateUrl(
                    todayCity?.timezone ?? trip.timezone,
                    todayCity?.countryCode,
                  ),
                  "_blank",
                  "noopener",
                )
              }
            >
              <Languages className="size-4" />
              구글 번역 열기
            </Button>
          </section>
        </>
      )}
    </div>
  );
}
