import { Search } from "lucide-react";
import { useState } from "react";

import { Input } from "@/components/ui/input";

import type { GeocodePlace } from "../api/geocode";
import { useSearchPlaces } from "../hooks/useGeocode";

interface LocationSearchProps {
  onSelect: (place: GeocodePlace) => void;
}

/** 장소 검색 입력 + 결과 목록. 선택 시 좌표·장소명을 상위로 올린다(지도와 독립이라 단위 테스트 대상). */
export function LocationSearch({ onSelect }: LocationSearchProps) {
  const [query, setQuery] = useState("");
  const { data: results, isFetching } = useSearchPlaces(query);

  return (
    <div className="relative">
      <div className="relative">
        <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="장소 검색"
          aria-label="장소 검색"
          className="pl-8"
        />
      </div>
      {query.trim().length >= 2 && (
        <ul className="border-border bg-background absolute z-10 mt-1 max-h-48 w-full overflow-y-auto rounded-md border shadow-md">
          {isFetching && (results ?? []).length === 0 ? (
            <li className="text-muted-foreground px-3 py-2 text-sm">
              검색 중...
            </li>
          ) : (results ?? []).length === 0 ? (
            <li className="text-muted-foreground px-3 py-2 text-sm">
              결과 없음
            </li>
          ) : (
            (results ?? []).map((place, i) => (
              <li key={`${place.lat}-${place.lng}-${i}`}>
                <button
                  type="button"
                  onClick={() => {
                    onSelect(place);
                    setQuery("");
                  }}
                  className="hover:bg-muted w-full px-3 py-2 text-left text-sm"
                >
                  {place.placeName ?? `${place.lat}, ${place.lng}`}
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
