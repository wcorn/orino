import { ChevronDown, MapPin } from "lucide-react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import type { BaseCity } from "@/features/travel/api/activities";

interface SearchCityChipProps {
  /** 지금 검색이 기준으로 삼는 도시. 여행에 도시가 없으면 null. */
  city: BaseCity | null;
  /** 이 여행에 등장하는 도시들. 하나뿐이면 고를 것이 없어 시트를 열지 않는다. */
  cities: BaseCity[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (city: BaseCity) => void;
}

/**
 * 검색 기준 도시 칩(§2.7).
 *
 * <p><b>어느 도시를 기준으로 도는지 화면이 말한다.</b> 다구간 여행에서 검색은 조용히 첫날
 * 도시로 편향되는데, 그 상태에서는 교토에서 검색해도 오사카 가게가 나온다. 무엇을 기준으로
 * 찾고 있는지 보이지 않으면 사용자는 결과가 이상한 이유를 알 수 없다.
 *
 * <p>도시가 하나뿐인 여행에서는 고를 것이 없다 — 칩은 그대로 두되(무엇을 기준으로 찾는지는
 * 여전히 정보다) 누를 수 없게 한다.
 */
export function SearchCityChip({
  city,
  cities,
  open,
  onOpenChange,
  onSelect,
}: SearchCityChipProps) {
  if (city === null) return null;
  const selectable = cities.length > 1;

  return (
    <div className="flex items-center gap-2">
      <span className="text-muted-foreground shrink-0 text-xs">검색 기준</span>
      <button
        type="button"
        disabled={!selectable}
        onClick={() => onOpenChange(true)}
        aria-label={`검색 기준 도시 ${city.name}`}
        className="border-primary bg-accent flex items-center gap-1 rounded-full border px-2.5 py-1 text-[13px] font-medium disabled:opacity-100"
      >
        <MapPin className="size-[13px] shrink-0" />
        {city.name}
        {selectable && <ChevronDown className="size-[13px] shrink-0" />}
      </button>

      <BottomSheet
        open={open}
        onOpenChange={onOpenChange}
        title="검색 기준 도시"
        description="이 여행에서 지나는 도시 중에 고릅니다"
      >
        <ul className="flex flex-col gap-1.5">
          {cities.map((candidate) => (
            <li key={candidate.placeId}>
              <button
                type="button"
                onClick={() => onSelect(candidate)}
                aria-pressed={candidate.placeId === city.placeId}
                className={`flex w-full items-center gap-2 rounded-lg border px-3 py-2.5 text-left text-sm ${
                  candidate.placeId === city.placeId
                    ? "border-primary bg-accent"
                    : "border-border hover:bg-accent"
                }`}
              >
                <MapPin className="text-muted-foreground size-4 shrink-0" />
                <span className="flex-1">{candidate.name}</span>
              </button>
            </li>
          ))}
        </ul>
      </BottomSheet>
    </div>
  );
}
