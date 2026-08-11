import { MapPin, Search, X } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { BaseCity } from "@/features/travel/api/activities";
import type { PlaceSearchResult } from "@/features/travel/api/places";
import { searchPlaces } from "@/features/travel/api/places";

/** 고른 숙소 장소. 저장할 때 `googlePlaceId`로 넘어간다. */
export interface PickedPlace {
  googlePlaceId: string;
  name: string;
  address: string | null;
}

interface StayPlacePickerProps {
  tripId: number;
  picked: PickedPlace | null;
  onPick: (place: PickedPlace | null) => void;
  /** 이 숙소가 있는 도시. 검색 편향이자 <b>저장될 도시 식별자</b>다. */
  city: BaseCity | null;
  cities: BaseCity[];
  onCityChange: (city: BaseCity) => void;
}

/**
 * 숙소에 붙일 장소를 찾는다(§9.6).
 *
 * <p><b>도시를 사용자가 고른다.</b> 그날 기준 도시에서 자동으로 가져오면 닛코 당일치기 날의
 * 도쿄 숙소가 "닛코 숙소"로 저장된다 — 숙소는 기준 도시와 무관하다는 것이 v2.1의 전제고
 * (§3.5), 그 전제를 저장 시점에 깨면 이동시간 판정이 그대로 틀어진다.
 *
 * <p>고른 도시는 검색 편향에도 쓴다. 교토 호텔을 찾는데 오사카 좌표로 물어볼 이유가 없다.
 */
export function StayPlacePicker({
  tripId,
  picked,
  onPick,
  city,
  cities,
  onCityChange,
}: StayPlacePickerProps) {
  const [draft, setDraft] = useState("");
  const [results, setResults] = useState<PlaceSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [failed, setFailed] = useState(false);

  const search = async (event: FormEvent) => {
    event.preventDefault();
    const q = draft.trim();
    if (!q) return;
    setSearching(true);
    setFailed(false);
    try {
      setResults(await searchPlaces(q, tripId, city?.placeId));
    } catch {
      setResults(null);
      setFailed(true);
    } finally {
      setSearching(false);
    }
  };

  if (picked) {
    return (
      <div className="border-border bg-card flex items-center gap-2 rounded-lg border px-3 py-2.5">
        <MapPin className="text-primary size-4 shrink-0" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{picked.name}</p>
          {picked.address && (
            <p className="text-muted-foreground truncate text-xs">
              {picked.address}
            </p>
          )}
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="장소 지우기"
          onClick={() => onPick(null)}
        >
          <X className="size-4" />
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      {/* 도시가 둘 이상일 때만 고를 것이 있다. */}
      {cities.length > 1 && (
        <div className="flex flex-wrap gap-1.5">
          {cities.map((candidate) => (
            <button
              key={candidate.placeId}
              type="button"
              aria-pressed={candidate.placeId === city?.placeId}
              onClick={() => onCityChange(candidate)}
              className={`flex items-center gap-1 rounded-full border px-2.5 py-1 text-[13px] ${
                candidate.placeId === city?.placeId
                  ? "border-primary bg-accent font-medium"
                  : "border-border hover:bg-accent"
              }`}
            >
              <MapPin className="size-[13px] shrink-0" />
              {candidate.name}
            </button>
          ))}
        </div>
      )}

      <div className="flex gap-2">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={city ? `${city.name} 숙소 검색` : "숙소 검색"}
          aria-label="숙소 장소 검색"
          // 시트 안이라 Enter가 바깥 폼을 제출해 버린다 — 여기서 가로챈다.
          onKeyDown={(e) => {
            if (e.key === "Enter") void search(e);
          }}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={searching}
          onClick={(e) => void search(e)}
        >
          <Search className="size-3.5" />
          검색
        </Button>
      </div>

      {failed && (
        <p className="text-muted-foreground text-xs">
          검색하지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      )}
      {results !== null && results.length === 0 && !searching && (
        // 장소 없이도 숙소는 저장된다 — 이동시간과 길찾기만 없다.
        <p className="text-muted-foreground text-xs">
          검색 결과가 없어요. 장소 없이 저장해도 됩니다.
        </p>
      )}

      {results !== null && results.length > 0 && (
        <ul className="flex max-h-[180px] flex-col gap-1 overflow-y-auto">
          {results.slice(0, 5).map((place) => (
            <li key={place.googlePlaceId}>
              <button
                type="button"
                onClick={() =>
                  onPick({
                    googlePlaceId: place.googlePlaceId,
                    name: place.name,
                    address: place.address,
                  })
                }
                className="hover:bg-accent flex w-full flex-col items-start rounded-lg px-2.5 py-2 text-left"
              >
                <span className="text-sm">{place.name}</span>
                {place.address && (
                  <span className="text-muted-foreground text-xs">
                    {place.address}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
