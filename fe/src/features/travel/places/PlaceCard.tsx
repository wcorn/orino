import { Button } from "@/components/ui/button";
import type { PlaceSearchResult } from "@/features/travel/api/places";

interface PlaceCardProps {
  place: PlaceSearchResult;
  onAdd: (place: PlaceSearchResult) => void;
  pending?: boolean;
}

/** 카테고리 · 평점 · 주소를 한 줄로 — 셋 다 없을 수 있어 있는 것만 잇는다. */
function metaLine(place: PlaceSearchResult): string {
  return [
    place.category,
    place.rating === null ? null : `★ ${place.rating.toFixed(1)}`,
    place.address,
  ]
    .filter(Boolean)
    .join(" · ");
}

/**
 * 검색 결과 카드(§S-06).
 *
 * <p>썸네일 자리가 없다 — 구글 장소 사진은 약관상 캐시할 수 없어 넣지 않기로 했고(D-16),
 * 영영 빈 자리 표시만 뜰 슬롯을 남겨두지 않는다.
 */
export function PlaceCard({ place, onAdd, pending = false }: PlaceCardProps) {
  const meta = metaLine(place);

  return (
    <li className="border-border bg-card flex items-center gap-3 rounded-xl border p-2.5">
      <div className="min-w-0 flex-1">
        <p className="truncate text-[15px] font-medium">{place.name}</p>
        {meta && (
          <p className="text-muted-foreground truncate text-xs">{meta}</p>
        )}
      </div>

      <Button
        variant="outline"
        size="sm"
        onClick={() => onAdd(place)}
        disabled={pending}
      >
        담기
      </Button>
    </li>
  );
}
