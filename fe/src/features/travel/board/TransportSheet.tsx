import { Car, Footprints, Navigation } from "lucide-react";
import { useEffect, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import type {
  Activity,
  TravelMode,
  TravelTime,
} from "@/features/travel/api/activities";
import { fetchTravelTime } from "@/features/travel/api/activities";
import { directionsUrl } from "@/features/travel/lib/mapsLink";
import { travelTimeLabel } from "@/features/travel/lib/travelTimeLabel";
import { toast } from "@/shared/lib/toast";

interface TransportSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tripId: number;
  /** 탭한 구간. 서버가 자동 판정한 수단의 값이 들어 있다. */
  travelTime: TravelTime | null;
  /** 딥링크 좌표를 얻으려면 양 끝 일정이 필요하다. */
  activities: Activity[];
}

const MODES: { mode: TravelMode; label: string; Icon: typeof Footprints }[] = [
  { mode: "WALK", label: "도보", Icon: Footprints },
  { mode: "DRIVE", label: "자동차", Icon: Car },
];

/**
 * 이동수단 시트(§S-04). 도보 / 자동차 / 구글 지도에서 길찾기.
 *
 * <p>보드는 직선거리로 정한 <b>한쪽 수단만</b> 내려준다(§1.3). 다른 쪽은 여기서 <b>고른 순간에만</b>
 * 부른다 — 열지도 않을 값을 미리 받아두면 그대로 비용이다(호출당 과금).
 */
export function TransportSheet({
  open,
  onOpenChange,
  tripId,
  travelTime,
  activities,
}: TransportSheetProps) {
  /** 수단별로 받아 둔 값. 시트를 닫았다 열어도 같은 구간이면 다시 부르지 않는다. */
  const [byMode, setByMode] = useState<Partial<Record<TravelMode, TravelTime>>>(
    {},
  );
  const [selected, setSelected] = useState<TravelMode | null>(null);
  const [loading, setLoading] = useState<TravelMode | null>(null);

  // 다른 구간을 탭하면 이전 구간의 값이 남아 있으면 안 된다.
  useEffect(() => {
    if (!travelTime) return;
    // 도시를 넘는 이동은 수단이 없다(§3.4). 보드가 이 시트 대신 지도로 보내므로 여기까지
    // 오지 않지만, 오더라도 없는 수단을 골라 둔 것처럼 보이면 안 된다.
    const mode = travelTime.mode;
    setByMode(mode === null ? {} : { [mode]: travelTime });
    setSelected(mode);
  }, [travelTime]);

  if (!travelTime) return null;

  const from = activities.find((a) => a.id === travelTime.fromActivityId);
  const to = activities.find((a) => a.id === travelTime.toActivityId);
  const mapsUrl =
    from?.place && to?.place ? directionsUrl(from.place, to.place) : null;

  const pick = async (mode: TravelMode) => {
    setSelected(mode);
    if (byMode[mode] || loading) return;

    setLoading(mode);
    try {
      const fetched = await fetchTravelTime(
        tripId,
        travelTime.fromActivityId,
        travelTime.toActivityId,
        mode,
      );
      setByMode((prev) => ({ ...prev, [mode]: fetched }));
    } catch {
      // 못 받아도 시트는 열려 있어야 한다 — 딥링크는 여전히 쓸 수 있다.
      toast("이동시간을 가져오지 못했어요", "error");
      setSelected(travelTime.mode);
    } finally {
      setLoading(null);
    }
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="이동"
      description={from && to ? `${from.title} → ${to.title}` : undefined}
    >
      <div className="flex flex-col gap-2">
        {MODES.map(({ mode, label, Icon }) => {
          const value = byMode[mode];
          return (
            <button
              key={mode}
              type="button"
              onClick={() => void pick(mode)}
              aria-pressed={selected === mode}
              className={`flex items-center gap-2.5 rounded-lg border px-3 py-2.5 text-left text-sm ${
                selected === mode
                  ? "border-primary bg-accent"
                  : "border-border hover:bg-accent"
              }`}
            >
              <Icon className="text-muted-foreground size-4 shrink-0" />
              <span className="flex-1">{label}</span>
              <span className="text-muted-foreground text-xs">
                {loading === mode
                  ? "…"
                  : value
                    ? travelTimeLabel(value)
                    : "확인"}
              </span>
            </button>
          );
        })}

        {/* 앱 내 표시가 도보/자동차여도 딥링크는 항상 대중교통이다(§4.5). */}
        <Button
          variant="outline"
          size="sm"
          className="mt-1 w-full"
          disabled={mapsUrl === null}
          onClick={() => mapsUrl && window.open(mapsUrl, "_blank", "noopener")}
        >
          <Navigation className="size-3.5" />
          구글 지도에서 길찾기 (대중교통)
        </Button>
      </div>
    </BottomSheet>
  );
}
