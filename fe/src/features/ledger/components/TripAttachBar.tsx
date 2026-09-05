import { Plane } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import { useTrips } from "@/features/travel/hooks/useTrips";

interface TripAttachBarProps {
  /** 고른 건수. 0이면 붙일 것이 없다. */
  selectedCount: number;
  onApply: (tripId: number) => void;
  onCancel: () => void;
  pending: boolean;
}

/**
 * 「여행에 붙이기」 하단 바(화면 §10.5).
 *
 * <p>여행 중엔 가계부에 그냥 적고, 돌아와 <b>기간으로 걸러 한 번</b> 붙인다. 경비 화면이
 * 다 밀려도 이 통로 하나면 「다녀와서 얼마 들었나」는 답이 나온다(명세 §18).
 *
 * <p>여행 목록은 <b>이 바가 열릴 때만</b> 부른다. 내역 화면 위쪽에서 부르면 가계부를 열 때마다
 * 여행 API가 함께 나가고, 그건 가계부 쪽 동작 변경이다(사이드바가 워크스페이스별로 훅을
 * 끄는 것과 같은 이유).
 */
export function TripAttachBar({
  selectedCount,
  onApply,
  onCancel,
  pending,
}: TripAttachBarProps) {
  const { data, isPending } = useTrips();
  const [tripId, setTripId] = useState<number | null>(null);

  const trips = useMemo(() => data?.trips ?? [], [data?.trips]);
  // 목록이 오면 가장 최근 여행을 미리 골라 둔다 — 대개 방금 다녀온 그 여행이다.
  useEffect(() => {
    if (tripId === null && trips.length > 0) {
      setTripId(trips[0].id);
    }
  }, [tripId, trips]);

  const selected = trips.find((trip) => trip.id === tripId);

  return (
    <div className="bg-background sticky bottom-0 flex flex-wrap items-center gap-3 border-t pt-3">
      <Plane className="text-muted-foreground size-4 shrink-0" />

      {isPending ? (
        <span className="text-muted-foreground text-[13px]">
          여행을 불러오는 중…
        </span>
      ) : trips.length === 0 ? (
        <span className="text-muted-foreground text-[13px]">
          아직 만든 여행이 없어요.
        </span>
      ) : (
        <>
          {/* Select는 문자열 값만 다룬다 — id를 문자열로 넘기고 받을 때 되돌린다. */}
          <Select
            value={String(tripId ?? "")}
            onValueChange={(value) => setTripId(Number(value))}
            ariaLabel="붙일 여행"
            options={trips.map((trip) => ({
              value: String(trip.id),
              label: trip.title,
            }))}
          />
          <span className="text-[13px]">
            고른 {selectedCount}건을 「{selected?.title ?? ""}」에 붙입니다
          </span>
        </>
      )}

      <div className="ml-auto flex items-center gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
          취소
        </Button>
        <Button
          type="button"
          size="sm"
          disabled={pending || selectedCount === 0 || tripId === null}
          onClick={() => tripId !== null && onApply(tripId)}
        >
          붙이기
        </Button>
      </div>
    </div>
  );
}
