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
 * <p>사진은 <b>이미 담아 둔 장소</b>에만 붙는다 — 검색 20건의 사진을 받으면 화면 한 번에
 * 유료 호출 20번이다. 사진이 있으면 저작자 표기를 함께 그린다(구글 약관).
 *
 * <p>`좋았던 곳` 배지는 응답 필드만 있고 서버가 아직 안 채운다(평점 판정은 별도 이슈).
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
        {/*
          사진 저작자 표기. 툴팁이 아니라 <b>보이게</b> 그린다 — 구글 약관이 요구하는 것은
          "표시"이고, 마우스를 올려야 보이는 것은 표시가 아니다. 폰에는 hover도 없다.
        */}
        {place.photoUrl && place.photoAttribution && (
          <p className="text-muted-foreground truncate text-[11px]">
            사진 © {place.photoAttribution}
          </p>
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
