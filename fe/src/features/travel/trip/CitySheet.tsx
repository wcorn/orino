import { MapPin, Search } from "lucide-react";
import { type FormEvent, useId, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import { type City, searchCities } from "@/features/travel/api/places";
import {
  CURRENCY_OPTIONS,
  TIMEZONE_OPTIONS,
} from "@/features/travel/lib/destinations";
import { GoogleAttribution } from "@/features/travel/places/GoogleAttribution";

type TimezoneValue = (typeof TIMEZONE_OPTIONS)[number]["value"];
type CurrencyValue = (typeof CURRENCY_OPTIONS)[number]["value"];

interface CitySheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 검색으로 고른 도시. 타임존·통화·좌표가 함께 온다. */
  onSelect: (city: City) => void;
  /** 검색이 막혔을 때 직접 입력한 도시. */
  onSelectManual: (
    cityName: string,
    timezone: string,
    currency: string,
  ) => void;
}

/**
 * 구간의 도시를 고르는 시트(§S-03).
 *
 * <p>고르면 <b>타임존·통화가 함께 정해진다</b> — 서버가 확정해 주므로 프론트가 좌표에서
 * 타임존을 유추하지 않는다. 고른 결과는 그대로 저장 요청에 실려 가고, 서버가 담아 도시로
 * 승격한다. <b>고르기 전에 저장하지 않는다</b> — 저장했다가 취소한 도시가 쌓이기 때문이다.
 *
 * <p><b>검색이 실패해도 여행은 만들 수 있어야 한다.</b> 키가 없거나 호출이 막히면 직접
 * 입력으로 넘어갈 길을 그 자리에서 준다. 그때는 타임존·통화를 정해 줄 사람이 없으므로
 * 사용자가 고른다.
 */
export function CitySheet({
  open,
  onOpenChange,
  onSelect,
  onSelectManual,
}: CitySheetProps) {
  const [draft, setDraft] = useState("");
  const [cities, setCities] = useState<City[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [failed, setFailed] = useState(false);
  const [manual, setManual] = useState(false);
  const [manualName, setManualName] = useState("");
  const [timezone, setTimezone] = useState<TimezoneValue>(
    TIMEZONE_OPTIONS[1].value,
  );
  const [currency, setCurrency] = useState<CurrencyValue>(
    CURRENCY_OPTIONS[1].value,
  );

  const timezoneLabelId = useId();
  const currencyLabelId = useId();

  const reset = () => {
    setDraft("");
    setCities(null);
    setFailed(false);
    setManual(false);
    setManualName("");
  };

  const close = () => {
    reset();
    onOpenChange(false);
  };

  const search = async (event: FormEvent) => {
    event.preventDefault();
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

  const pick = (city: City) => {
    onSelect(city);
    close();
  };

  const pickManual = () => {
    const name = manualName.trim();
    if (!name) return;
    onSelectManual(name, timezone, currency);
    close();
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title="도시 선택"
      description="고르면 타임존과 통화가 함께 정해져요."
    >
      {manual ? (
        <div className="mt-3 flex flex-col gap-3">
          <FormField label="도시 이름" htmlFor="manualCityName">
            <Input
              id="manualCityName"
              value={manualName}
              onChange={(e) => setManualName(e.target.value)}
              placeholder="도쿄"
              maxLength={100}
            />
          </FormField>
          <div className="flex gap-3">
            <FormField
              label="타임존"
              labelId={timezoneLabelId}
              className="min-w-0 flex-1"
            >
              <Select
                value={timezone}
                onValueChange={setTimezone}
                options={[...TIMEZONE_OPTIONS]}
                ariaLabelledby={timezoneLabelId}
              />
            </FormField>
            <FormField
              label="통화"
              labelId={currencyLabelId}
              className="min-w-0 flex-1"
            >
              <Select
                value={currency}
                onValueChange={setCurrency}
                options={[...CURRENCY_OPTIONS]}
                ariaLabelledby={currencyLabelId}
              />
            </FormField>
          </div>
          <div className="flex justify-between">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setManual(false)}
            >
              검색으로 고르기
            </Button>
            <Button
              type="button"
              onClick={pickManual}
              disabled={!manualName.trim()}
            >
              이 도시로
            </Button>
          </div>
        </div>
      ) : (
        <div className="mt-3 flex flex-col gap-2">
          <FormField label="도시 검색" htmlFor="citySearch">
            <div className="flex items-center gap-2">
              <Search className="text-muted-foreground size-4 shrink-0" />
              <Input
                id="citySearch"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") void search(e);
                }}
                placeholder="교토"
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

          {searching && <LoadingText />}

          {failed && (
            <div className="border-border flex flex-col items-start gap-2 rounded-lg border p-3">
              <p className="text-muted-foreground text-sm">
                도시를 검색하지 못했어요.
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setManual(true)}
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
                    onClick={() => setManual(true)}
                  >
                    직접 입력하기
                  </Button>
                </li>
              ) : (
                cities.map((city) => (
                  <li key={city.googlePlaceId}>
                    <button
                      type="button"
                      onClick={() => pick(city)}
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
              {cities.length > 0 && (
                <li>
                  <GoogleAttribution />
                </li>
              )}
            </ul>
          )}
        </div>
      )}
    </BottomSheet>
  );
}
