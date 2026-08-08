import { MapPin, Search } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import type { City } from "@/features/travel/api/places";
import { searchCities } from "@/features/travel/api/places";
import { GoogleAttribution } from "@/features/travel/places/GoogleAttribution";

interface DestinationSearchProps {
  /** 고른 목적지 이름. 검색창의 값이 아니라 확정된 값이다. */
  value: string;
  onSelect: (city: City) => void;
  /** 검색을 포기하고 직접 입력으로 넘어간다. */
  onFallback: () => void;
}

/**
 * 목적지 도시 검색(§S-03).
 *
 * <p>고르면 타임존·통화·좌표가 함께 정해진다 — 서버가 확정해 주므로 프론트가 좌표에서
 * 타임존을 유추하지 않는다.
 *
 * <p><b>검색이 실패해도 여행은 만들 수 있어야 한다.</b> 키가 없거나 호출이 실패하면
 * 직접 입력으로 넘어갈 길을 그 자리에서 준다 — 여행 만들기가 외부 API에 걸려 막히면 안 된다.
 */
export function DestinationSearch({
  value,
  onSelect,
  onFallback,
}: DestinationSearchProps) {
  const [draft, setDraft] = useState("");
  const [cities, setCities] = useState<City[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [failed, setFailed] = useState(false);

  const search = async (event: FormEvent) => {
    event.preventDefault();
    // 이 폼은 여행 만들기 폼 안에 있다 — 제출이 위로 새면 여행이 저장돼 버린다.
    event.stopPropagation();
    const q = draft.trim();
    if (!q) return;

    setSearching(true);
    setFailed(false);
    try {
      setCities(await searchCities(q));
    } catch {
      setCities(null);
      setFailed(true);
    } finally {
      setSearching(false);
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <FormField label="목적지 도시" htmlFor="destinationSearch">
        <div className="flex items-center gap-2">
          <Search className="text-muted-foreground size-4 shrink-0" />
          <Input
            id="destinationSearch"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              // Enter가 바깥 폼으로 새면 목적지도 안 고른 채 저장된다.
              if (e.key === "Enter") void search(e);
            }}
            placeholder={value || "도쿄"}
            maxLength={100}
          />
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={(e) => void search(e)}
            disabled={searching || !draft.trim()}
          >
            검색
          </Button>
        </div>
      </FormField>

      {value && (
        <p className="text-muted-foreground flex items-center gap-1 text-xs">
          <MapPin className="size-3.5" />
          선택한 목적지: <span className="text-foreground">{value}</span>
        </p>
      )}

      {searching && <LoadingText />}

      {failed && (
        <div className="border-border flex flex-col items-start gap-2 rounded-lg border p-3">
          <p className="text-muted-foreground text-sm">
            목적지를 검색하지 못했어요.
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onFallback}
          >
            직접 입력하기
          </Button>
        </div>
      )}

      {cities !== null && !searching && (
        <ul className="bg-popover border-border overflow-hidden rounded-lg border">
          {cities.length === 0 ? (
            <li className="flex flex-col items-start gap-2 p-3">
              <p className="text-muted-foreground text-sm">
                검색 결과가 없어요.
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onFallback}
              >
                직접 입력하기
              </Button>
            </li>
          ) : (
            cities.map((city) => (
              <li key={city.googlePlaceId}>
                <button
                  type="button"
                  onClick={() => {
                    onSelect(city);
                    setCities(null);
                    setDraft("");
                  }}
                  className="hover:bg-accent flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm"
                >
                  <MapPin className="text-muted-foreground size-3.5 shrink-0" />
                  <span className="flex-1 truncate">{city.name}</span>
                  <span className="text-muted-foreground shrink-0 text-xs">
                    {city.timezone} · {city.currency}
                  </span>
                </button>
              </li>
            ))
          )}
          {/* 지도 없이 Places 데이터(도시명·타임존)를 보여주는 목록이라 출처를 표기한다. */}
          {cities !== null && cities.length > 0 && (
            <li>
              <GoogleAttribution />
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
