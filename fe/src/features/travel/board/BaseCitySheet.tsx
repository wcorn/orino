import { Check, MapPin, Search, StickyNote } from "lucide-react";
import { type FormEvent, useEffect, useRef, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import type { BoardDay } from "@/features/travel/api/activities";
import type { DayUpdateRequest } from "@/features/travel/api/days";
import { type City, searchCities } from "@/features/travel/api/places";
import {
  failureOf,
  type SearchFailure,
} from "@/features/travel/lib/searchFailure";
import { formatShortDate } from "@/features/travel/lib/tripStatus";
import { GoogleAttribution } from "@/features/travel/places/GoogleAttribution";
import { SearchUnavailable } from "@/features/travel/places/SearchUnavailable";
import { cn } from "@/lib/utils";

const MEMO_MAX = 200;

/**
 * 시트가 열린 직후 이 시간 안에 들어온 바깥 클릭은 무시한다.
 *
 * <p>이 시트를 여는 손짓은 <b>탭을 누른 채 기다리는 것</b>이다. 450ms에 시트가 올라오고 그
 * 다음에 손가락이 떨어지는데, 그 순간 손끝은 이미 시트 <b>바깥</b>(스크림 위)에 있다 — 여는
 * 동작의 마지막 절반이 곧바로 닫는 동작이 된다. 실제 브라우저에서만 드러나는 문제라 E2E가
 * 먼저 잡았다.
 */
const OPEN_GUARD_MS = 400;

interface BaseCitySheetProps {
  /** 편집할 날짜. null이면 시트가 닫혀 있다. */
  day: BoardDay | null;
  /** 이 여행에 이미 등장하는 도시들 — 가장 자주 고르는 후보다. */
  tripCities: { placeId: number; name: string }[];
  onOpenChange: (open: boolean) => void;
  onSubmit: (body: DayUpdateRequest) => void;
  pending: boolean;
}

/** 아직 저장하지 않은 선택. 고른 방식 그대로 들고 있다가 저장할 때 서버에 넘긴다. */
type Picked =
  | { kind: "saved"; placeId: number; name: string }
  | { kind: "google"; googlePlaceId: string; name: string };

/**
 * 날짜 탭을 450ms 길게 눌러 여는 시트(§S-04) — 그날의 <b>기준 도시</b>와 도시 메모를 고친다.
 *
 * <p>후보를 두 층으로 나눈다. 위는 <b>이 여행에 이미 있는 도시</b>다 — 도쿄에서 하루 닛코에
 * 다녀오고 다시 도쿄로 돌아오는 식의 변경이 대부분이라, 그때마다 검색을 시키면 손이 는다.
 * 아래는 검색이다. 검색으로 고른 도시는 <b>고른 그대로</b> 보내고 서버가 담으면서 도시
 * 식별자를 붙인다 — 화면이 먼저 장소를 만들면 그 식별자가 없어 그날 일정이 전부 "다른 도시"로
 * 표시된다.
 *
 * <p>도시를 바꿔도 <b>이미 담긴 일정의 장소는 그대로 두고 경고하지 않는다.</b> 오사카 가게를
 * 교토 날짜에 두는 건 사용자의 선택이다.
 */
export function BaseCitySheet({
  day,
  tripCities,
  onOpenChange,
  onSubmit,
  pending,
}: BaseCitySheetProps) {
  const [picked, setPicked] = useState<Picked | null>(null);
  const [memo, setMemo] = useState("");
  const [draft, setDraft] = useState("");
  const [results, setResults] = useState<City[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [failure, setFailure] = useState<SearchFailure>(null);

  const openedAt = useRef(0);

  // 다른 날짜를 열면 그 날짜의 값에서 다시 시작한다. 앞 날짜에서 고르다 만 도시가 남아
  // 있으면, 저장 버튼 하나로 엉뚱한 날짜의 도시가 바뀐다.
  useEffect(() => {
    setPicked(null);
    setMemo(day?.cityMemo ?? "");
    setDraft("");
    setResults(null);
    setFailure(null);
    if (day) openedAt.current = performance.now();
  }, [day?.dayId, day?.cityMemo, day]);

  const handleOpenChange = (open: boolean, reason?: string) => {
    if (
      !open &&
      reason === "outside-press" &&
      performance.now() - openedAt.current < OPEN_GUARD_MS
    ) {
      return;
    }
    onOpenChange(open);
  };

  const search = async (event: FormEvent) => {
    event.preventDefault();
    const q = draft.trim();
    if (!q) return;
    setSearching(true);
    setFailure(null);
    try {
      setResults(await searchCities(q));
    } catch (error) {
      setResults(null);
      setFailure(failureOf(error));
    } finally {
      setSearching(false);
    }
  };

  const submit = () => {
    const body: DayUpdateRequest = {};
    if (picked?.kind === "saved") body.baseCityPlaceId = picked.placeId;
    if (picked?.kind === "google")
      body.baseCityGooglePlaceId = picked.googlePlaceId;
    // 메모는 바뀌었을 때만 보낸다 — 생략은 "안 바꿈", 빈 문자열은 "지움"이다.
    if (memo.trim() !== (day?.cityMemo ?? "")) body.cityMemo = memo.trim();
    onSubmit(body);
  };

  const currentName = picked?.name ?? day?.baseCity?.name ?? "도시 없음";
  const isCurrent = (placeId: number) =>
    picked === null
      ? day?.baseCity?.placeId === placeId
      : picked.kind === "saved" && picked.placeId === placeId;

  return (
    <BottomSheet
      open={day !== null}
      onOpenChange={handleOpenChange}
      title="기준 도시"
      description={
        day
          ? `${day.dayIndex}일차 ${formatShortDate(day.date)} · 지금은 ${currentName}`
          : undefined
      }
    >
      <div className="mt-3 flex flex-col gap-4">
        {tripCities.length > 0 && (
          <section className="flex flex-col gap-1.5">
            <h3 className="text-muted-foreground text-[13px]">
              이 여행의 도시
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {tripCities.map((city) => (
                <Button
                  key={city.placeId}
                  type="button"
                  variant={isCurrent(city.placeId) ? "default" : "outline"}
                  size="sm"
                  onClick={() =>
                    setPicked({
                      kind: "saved",
                      placeId: city.placeId,
                      name: city.name,
                    })
                  }
                >
                  {isCurrent(city.placeId) && <Check className="size-3.5" />}
                  {city.name}
                </Button>
              ))}
            </div>
          </section>
        )}

        <FormField label="도시 검색" htmlFor="baseCitySearch">
          <div className="flex items-center gap-2">
            <Search className="text-muted-foreground size-4 shrink-0" />
            <Input
              id="baseCitySearch"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") void search(e);
              }}
              placeholder="닛코"
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
        <SearchUnavailable failure={failure} subject="도시" />

        {results !== null && !searching && (
          <ul className="bg-popover border-border overflow-hidden rounded-lg border">
            {results.length === 0 ? (
              <li className="text-muted-foreground p-3 text-sm">
                검색 결과가 없어요.
              </li>
            ) : (
              results.map((city) => (
                <li key={city.googlePlaceId}>
                  <button
                    type="button"
                    onClick={() =>
                      setPicked({
                        kind: "google",
                        googlePlaceId: city.googlePlaceId,
                        name: city.name,
                      })
                    }
                    className={cn(
                      "hover:bg-accent flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm",
                      picked?.kind === "google" &&
                        picked.googlePlaceId === city.googlePlaceId &&
                        "bg-accent",
                    )}
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
            {results.length > 0 && (
              <li>
                <GoogleAttribution />
              </li>
            )}
          </ul>
        )}

        <FormField label="도시 메모" htmlFor="cityMemo">
          <div className="flex items-center gap-2">
            <StickyNote className="text-muted-foreground size-3.5 shrink-0" />
            <Input
              id="cityMemo"
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              placeholder="체크아웃 후 코인로커에 짐 보관"
              maxLength={MEMO_MAX}
            />
          </div>
        </FormField>

        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button type="button" onClick={submit} disabled={pending}>
            저장
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}
