import { ArrowLeft, List, MapPin, WifiOff } from "lucide-react";
import { lazy, Suspense, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { useBoard } from "@/features/travel/hooks/useBoard";
import { useCityLegs } from "@/features/travel/hooks/useCityLegs";
import { cityLabelOfDays } from "@/features/travel/lib/cityLabel";
import { cityMarkers, legPath } from "@/features/travel/lib/cityMarkers";
import { formatShortDate } from "@/features/travel/lib/tripStatus";
import { toMapped } from "@/features/travel/map/toMapped";
import type { MapPoint } from "@/features/travel/map/TripMap";
import { useOnline } from "@/shared/lib/useOnline";

// 지도 SDK는 무겁고 이 화면에서만 쓴다 — 보드 진입 비용에 얹지 않는다.
const TripMap = lazy(() =>
  import("@/features/travel/map/TripMap").then((m) => ({ default: m.TripMap })),
);

/** `?mode=` 값. 기본은 하루다 — 이 화면이 늘 답하던 질문이 "오늘 이 순서가 말이 되나"다. */
type Mode = "day" | "all";

/**
 * S-05 지도 뷰.
 *
 * <p>두 질문에 답한다 — `이 날짜`는 <b>오늘 동선이 말이 되는지</b>, `전체`는 <b>여행이 어떤
 * 모양인지</b>. 하루가 본체이므로 기본값은 `이 날짜`다. 여행 전체를 일정 단위로 겹쳐 그리면
 * 동선이 아니라 얼룩이 되므로, `전체`는 일정이 아니라 <b>도시</b>를 찍는다.
 *
 * <p><b>모드는 URL이 소유한다</b>(`?mode=day|all`, §9.7) — 새로고침·뒤로가기에서 살아남아야
 * 한다.
 */
export function TripMapPage() {
  const { tripId: tripIdParam } = useParams();
  const tripId = Number(tripIdParam);
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const online = useOnline();

  const day = searchParams.get("day");
  const dayIndex = day !== null ? Number(day) : null;
  const mode: Mode = searchParams.get("mode") === "all" ? "all" : "day";

  const { data: base, isPending } = useBoard(tripId, {});
  const requestedDate =
    dayIndex !== null ? base?.days[dayIndex]?.date : undefined;
  const needsOwnQuery =
    requestedDate !== undefined && requestedDate !== base?.selectedDate;
  const { data: dayBoard } = useBoard(
    tripId,
    { date: requestedDate },
    { enabled: needsOwnQuery },
  );
  const board = needsOwnQuery ? dayBoard : base;
  // `전체`를 보지 않는 동안에는 부르지 않는다 — 아무도 보지 않을 값이다.
  const { data: legs } = useCityLegs(tripId, { enabled: mode === "all" });

  /** 선택된 점. `이 날짜`에서는 일정 id, `전체`에서는 도시 placeId다. */
  const [selectedKey, setSelectedKey] = useState<number | null>(null);

  const backToList = () => {
    const suffix = day === null ? "" : `?day=${day}`;
    navigate(`/travel/trips/${tripId}/board${suffix}`);
  };

  /** 모드만 바꾼다 — 보던 날짜는 그대로 둔다. 돌아왔을 때 그 날짜여야 한다. */
  const switchMode = (next: Mode) => {
    const merged = new URLSearchParams(searchParams);
    merged.set("mode", next);
    setSearchParams(merged, { replace: true });
    setSelectedKey(null);
  };

  if (isPending || !board) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const mapped = toMapped(board.activities);
  const markers = cityMarkers(legs ?? []);
  const dayNumber =
    board.days.findIndex((d) => d.date === board.selectedDate) + 1;

  const points: MapPoint[] =
    mode === "all"
      ? markers.map((city) => ({
          key: city.cityPlaceId,
          order: city.legIndex,
          lat: city.lat,
          lng: city.lng,
          title: city.cityName,
        }))
      : mapped.map((m) => ({
          key: m.activity.id,
          order: m.order,
          lat: m.lat,
          lng: m.lng,
          title: m.activity.title,
        }));

  const selectedActivity = mapped.find((m) => m.activity.id === selectedKey);
  const selectedCity = markers.find((city) => city.cityPlaceId === selectedKey);

  /** 그 구간 첫날의 보드로 간다. 날짜 탭은 인덱스로 고르므로 날짜를 인덱스로 되돌린다. */
  const openLegFirstDay = (startDate: string) => {
    const index = board.days.findIndex((d) => d.date === startDate);
    navigate(
      `/travel/trips/${tripId}/board${index >= 0 ? `?day=${index}` : ""}`,
    );
  };

  const empty = mode === "all" ? markers.length === 0 : mapped.length === 0;

  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3 px-4 pt-3">
      <header className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="뒤로"
          onClick={backToList}
        >
          <ArrowLeft className="size-4" />
        </Button>
        <div className="min-w-0 flex-1">
          <h1 className="text-heading font-semibold">
            {mode === "all" ? "여행 전체" : `${dayNumber}일차 동선`}
          </h1>
          <p className="text-muted-foreground text-xs">
            {mode === "all"
              ? `도시 ${markers.length}곳 · 구간 순서`
              : `장소가 있는 일정 ${mapped.length}개 · 직선 연결`}
          </p>
        </div>
        <div
          role="group"
          aria-label="지도 범위"
          className="bg-muted flex gap-0.5 rounded-lg p-[3px]"
        >
          {(
            [
              { value: "day", label: "이 날짜" },
              { value: "all", label: "전체" },
            ] as const
          ).map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={mode === option.value}
              onClick={() => switchMode(option.value)}
              className={`h-7 rounded-md px-2.5 text-[13px] font-medium ${
                mode === option.value
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="리스트로 전환"
          onClick={backToList}
        >
          <List className="size-4" />
        </Button>
      </header>

      {!online ? (
        <EmptyState className="min-h-[40svh]">
          <WifiOff className="text-muted-foreground size-6" />
          <p className="text-muted-foreground text-sm">
            오프라인에서는 지도를 볼 수 없어요.
          </p>
          <Button variant="outline" onClick={backToList}>
            <List className="size-4" />
            리스트로 전환
          </Button>
        </EmptyState>
      ) : empty ? (
        <EmptyState className="min-h-[40svh]">
          <p className="text-muted-foreground text-sm">
            {mode === "all"
              ? "좌표를 아는 도시가 없어요."
              : "장소가 있는 일정이 없어요."}
          </p>
          {mode === "day" && (
            <Button
              variant="outline"
              onClick={() => navigate(`/travel/trips/${tripId}/places`)}
            >
              장소 검색
            </Button>
          )}
        </EmptyState>
      ) : (
        <>
          <Suspense
            fallback={
              <div className="bg-muted grid aspect-[8/5] w-full place-items-center rounded-lg border">
                <LoadingText />
              </div>
            }
          >
            <TripMap
              points={points}
              // 도시를 오가면 점보다 획이 많다 — 마커를 따라 이으면 다녀온 사실이 사라진다.
              path={mode === "all" ? legPath(legs ?? []) : undefined}
              selectedKey={selectedKey}
              onSelect={setSelectedKey}
              label={mode === "all" ? "여행 전체 지도" : "하루 동선 지도"}
            />
          </Suspense>

          {mode === "day" && selectedActivity && (
            <div className="border-border bg-card flex items-center gap-3 rounded-xl border px-3.5 py-3">
              <span className="bg-primary text-primary-foreground grid size-[26px] shrink-0 place-items-center rounded-full text-xs font-semibold">
                {selectedActivity.order}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-muted-foreground text-xs tabular-nums">
                  {[
                    selectedActivity.activity.startTime,
                    cityLabelOfDays(
                      selectedActivity.activity.place,
                      board.days,
                    ),
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
                <p className="truncate text-[15px] font-medium">
                  {selectedActivity.activity.title}
                </p>
                {selectedActivity.activity.place?.address && (
                  <p className="text-muted-foreground truncate text-xs">
                    {selectedActivity.activity.place.address}
                  </p>
                )}
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  navigate(`/travel/activities/${selectedActivity.activity.id}`)
                }
              >
                일정 열기
              </Button>
            </div>
          )}

          {mode === "all" && selectedCity && (
            <LegCard
              leg={legs?.find((l) => l.legIndex === selectedCity.legIndex)}
              onOpen={openLegFirstDay}
            />
          )}

          {/* 구간 리스트 — 지도가 못 하는 일을 한다. 어느 도시에 며칠 머무는지는 글자가 낫다. */}
          {mode === "all" && (
            <ul className="flex flex-col gap-1.5 pb-6">
              {(legs ?? []).map((leg) => (
                <li key={leg.legIndex}>
                  <button
                    type="button"
                    onClick={() => openLegFirstDay(leg.startDate)}
                    className="border-border bg-card hover:bg-accent flex w-full items-center gap-2.5 rounded-lg border px-3 py-2.5 text-left"
                  >
                    <span className="bg-muted grid size-[22px] shrink-0 place-items-center rounded-full text-xs font-semibold tabular-nums">
                      {leg.legIndex}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-[15px] font-medium">
                      {leg.cityName ?? "도시 없음"}
                    </span>
                    <span className="text-muted-foreground shrink-0 text-xs tabular-nums">
                      {leg.days}일 · {formatShortDate(leg.startDate)}–
                      {formatShortDate(leg.endDate)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

/** 마커를 탭했을 때 나오는 도시 카드. 지도 위에서 고른 것이 무엇인지 글자로 확인시킨다. */
function LegCard({
  leg,
  onOpen,
}: {
  leg:
    | {
        legIndex: number;
        cityName: string | null;
        days: number;
        startDate: string;
        endDate: string;
        timezone: string | null;
      }
    | undefined;
  onOpen: (startDate: string) => void;
}) {
  if (!leg) return null;
  return (
    <div className="border-border bg-card flex items-center gap-3 rounded-xl border px-3.5 py-3">
      <MapPin className="text-muted-foreground size-4 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-muted-foreground text-xs tabular-nums">
          {leg.days}일 · 구간 {leg.legIndex}
        </p>
        <p className="truncate text-[15px] font-medium">
          {leg.cityName ?? "도시 없음"}
        </p>
        <p className="text-muted-foreground truncate text-xs">
          {[
            `${formatShortDate(leg.startDate)} – ${formatShortDate(leg.endDate)}`,
            leg.timezone,
          ]
            .filter(Boolean)
            .join(" · ")}
        </p>
      </div>
      <Button variant="outline" size="sm" onClick={() => onOpen(leg.startDate)}>
        첫날 열기
      </Button>
    </div>
  );
}
