import { Image as ImageIcon, Star } from "lucide-react";

import { Badge } from "@/components/ui/badge";
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
 * <p>사진과 `좋았던 곳` 배지는 응답 필드가 이미 있고 서버가 채우기 시작하면 그대로 뜬다
 * (사진 #1058, 평점 기록은 4단계). 그때 이 컴포넌트는 손대지 않는다.
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
        <p className="flex items-center gap-1.5 text-[15px] font-medium">
          <span className="truncate">{place.name}</span>
          {place.loved && (
            <Badge variant="warning" className="shrink-0 gap-0.5">
              <Star className="size-[11px]" />
              좋았던 곳
            </Badge>
          )}
        </p>
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
