import { ArrowLeft, List, WifiOff } from "lucide-react";
import { lazy, Suspense, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { useBoard } from "@/features/travel/hooks/useBoard";
import { toMapped } from "@/features/travel/map/toMapped";
import { useOnline } from "@/shared/lib/useOnline";

// leaflet은 무겁고 이 화면에서만 쓴다 — 보드 진입 비용에 얹지 않는다.
const TripDayMap = lazy(() =>
  import("@/features/travel/map/TripDayMap").then((m) => ({
    default: m.TripDayMap,
  })),
);

/**
 * S-05 지도 뷰. <b>선택한 날짜의 동선만</b> 그린다.
 *
 * <p>여행 전체를 겹쳐 그리면 동선이 아니라 얼룩이 된다. 이 화면이 답하는 질문은
 * "오늘 이 순서가 말이 되나"이고, 그건 하루 단위로만 성립한다.
 */
export function TripMapPage() {
  const { tripId: tripIdParam } = useParams();
  const tripId = Number(tripIdParam);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const online = useOnline();

  const day = searchParams.get("day");
  const dayIndex = day !== null ? Number(day) : null;

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

  const [selectedId, setSelectedId] = useState<number | null>(null);

  const backToList = () => {
    const suffix = day === null ? "" : `?day=${day}`;
    navigate(`/travel/trips/${tripId}/board${suffix}`);
  };

  if (isPending || !board) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const mapped = toMapped(board.activities);
  const selected = mapped.find((m) => m.activity.id === selectedId);
  const dayNumber =
    board.days.findIndex((d) => d.date === board.selectedDate) + 1;

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
          <h1 className="text-heading font-semibold">{dayNumber}일차 동선</h1>
          <p className="text-muted-foreground text-xs">
            장소가 있는 일정 {mapped.length}개 · 직선 연결
          </p>
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
      ) : mapped.length === 0 ? (
        <EmptyState className="min-h-[40svh]">
          <p className="text-muted-foreground text-sm">
            장소가 있는 일정이 없어요.
          </p>
          <Button
            variant="outline"
            onClick={() => navigate(`/travel/trips/${tripId}/places`)}
          >
            장소 검색
          </Button>
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
            <TripDayMap
              mapped={mapped}
              selectedId={selectedId}
              onSelect={setSelectedId}
            />
          </Suspense>

          {selected && (
            <div className="border-border bg-card flex items-center gap-3 rounded-xl border px-3.5 py-3">
              <span className="bg-primary text-primary-foreground grid size-[26px] shrink-0 place-items-center rounded-full text-xs font-semibold">
                {selected.order}
              </span>
              <div className="min-w-0 flex-1">
                {selected.activity.startTime && (
                  <p className="text-muted-foreground text-xs tabular-nums">
                    {selected.activity.startTime}
                  </p>
                )}
                <p className="truncate text-[15px] font-medium">
                  {selected.activity.title}
                </p>
                {selected.activity.place?.address && (
                  <p className="text-muted-foreground truncate text-xs">
                    {selected.activity.place.address}
                  </p>
                )}
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  navigate(`/travel/activities/${selected.activity.id}`)
                }
              >
                일정 열기
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
