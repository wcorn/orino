import { Image as ImageIcon } from "lucide-react";

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
 * <p>사진은 응답 필드만 있고 서버가 채우지 않는다 — 구글 장소 사진은 약관상 캐시할 수 없어
 * 넣지 않기로 했다(결정 기록 D-16). 자리 표시 아이콘이 그 자리를 대신한다.
 */
export function PlaceCard({ place, onAdd, pending = false }: PlaceCardProps) {
  const meta = metaLine(place);

  return (
    <li className="border-border bg-card flex items-center gap-3 rounded-xl border p-2.5">
      {place.photoUrl ? (
        <img
          src={place.photoUrl}
          alt=""
          className="size-16 shrink-0 rounded-lg object-cover"
        />
      ) : (
        <div className="bg-muted flex size-16 shrink-0 items-center justify-center rounded-lg">
          <ImageIcon className="text-muted-foreground size-[18px]" />
        </div>
      )}

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
