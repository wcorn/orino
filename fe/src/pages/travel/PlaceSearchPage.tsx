import { ArrowLeft, Clock, Pencil, Search, X } from "lucide-react";
import { type FormEvent, useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import type { PlaceSearchResult } from "@/features/travel/api/places";
import { createManualPlace } from "@/features/travel/api/places";
import { useCreateActivity } from "@/features/travel/hooks/useActivityMutations";
import { useBoard } from "@/features/travel/hooks/useBoard";
import { usePlaceSearch } from "@/features/travel/hooks/usePlaceSearch";
import {
  addRecentSearch,
  clearRecentSearches,
  getRecentSearches,
} from "@/features/travel/lib/recentSearches";
import { PickDaySheet } from "@/features/travel/places/PickDaySheet";
import { PlaceCard } from "@/features/travel/places/PlaceCard";
import { toast } from "@/shared/lib/toast";

/** 담기 대상 — 검색 결과이거나, 직접 입력해서 방금 만든 장소. */
type Target =
  | { kind: "google"; googlePlaceId: string; name: string }
  | { kind: "manual"; placeId: number; name: string };

/**
 * S-06 장소 검색.
 *
 * <p><b>검색어는 URL이 소유한다</b>(`?q=`). 담고 나서 뒤로 오거나 새로고침해도 결과가 그대로
 * 남아야 한다 — 현지에서 여러 곳을 연달아 담을 때 매번 다시 치게 하면 쓸 수 없다.
 *
 * <p>타이핑마다 검색하지 않는다. Places는 호출당 과금이라 자동완성처럼 부르면 비용이 검색어
 * 길이에 비례한다. 제출(엔터·버튼)했을 때만 부른다.
 */
export function PlaceSearchPage() {
  const { tripId: tripIdParam } = useParams();
  const tripId = Number(tripIdParam);
  const navigate = useNavigate();

  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const [draft, setDraft] = useState(query);

  const [recent, setRecent] = useState<string[]>(() =>
    Number.isFinite(tripId) ? getRecentSearches(tripId) : [],
  );
  const [target, setTarget] = useState<Target | null>(null);
  const [manualOpen, setManualOpen] = useState(false);
  const [manualName, setManualName] = useState("");
  const [manualAddress, setManualAddress] = useState("");

  // 뒤로 가기로 검색어가 바뀌면 입력창도 따라가야 한다.
  useEffect(() => setDraft(query), [query]);

  const { data: places, isPending, isError } = usePlaceSearch(query, tripId);
  const { data: board } = useBoard(tripId, {});
  const createActivity = useCreateActivity(tripId);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed) return;
    setRecent(addRecentSearch(tripId, trimmed));
    setSearchParams({ q: trimmed });
  };

  const searchFor = (q: string) => {
    setRecent(addRecentSearch(tripId, q));
    setSearchParams({ q });
  };

  const add = (date: string | null) => {
    if (!target) return;
    createActivity.mutate(
      {
        title: target.name,
        activityDate: date,
        ...(target.kind === "google"
          ? { googlePlaceId: target.googlePlaceId }
          : { placeId: target.placeId }),
      },
      {
        onSuccess: () => {
          const where =
            date === null
              ? "보관함"
              : `${board?.days.find((d) => d.date === date)?.dayIndex ?? ""}일차`;
          toast(`${where}에 담았어요`, "success");
          setTarget(null);
        },
        onError: () =>
          toast("담지 못했어요. 잠시 후 다시 시도해 주세요", "error"),
      },
    );
  };

  const submitManual = async (event: FormEvent) => {
    event.preventDefault();
    const name = manualName.trim();
    if (!name) return;
    try {
      const place = await createManualPlace({
        name,
        address: manualAddress.trim() || null,
      });
      setManualOpen(false);
      setManualName("");
      setManualAddress("");
      // 만들자마자 날짜를 물어야 한다 — 장소만 만들어 두면 어디에도 보이지 않는다.
      setTarget({ kind: "manual", placeId: place.id, name: place.name });
    } catch {
      toast("장소를 만들지 못했어요", "error");
    }
  };

  const results = places ?? [];

  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col gap-3 px-4 pt-3">
      <form className="flex items-center gap-2" onSubmit={submit}>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label="뒤로"
          onClick={() => navigate(`/travel/trips/${tripId}/board`)}
        >
          <ArrowLeft className="size-4" />
        </Button>
        <Search className="text-muted-foreground size-4 shrink-0" />
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="장소 검색"
          aria-label="장소 검색"
          maxLength={100}
          autoFocus
        />
      </form>

      {query === "" ? (
        recent.length === 0 ? (
          <EmptyState className="min-h-[30svh]">
            <p className="text-muted-foreground text-sm">
              가고 싶은 곳을 검색해 보세요
            </p>
          </EmptyState>
        ) : (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <p className="text-muted-foreground text-xs">최근 검색어</p>
              <button
                type="button"
                onClick={() => {
                  clearRecentSearches(tripId);
                  setRecent([]);
                }}
                className="text-muted-foreground hover:text-foreground flex items-center gap-0.5 text-xs"
              >
                <X className="size-3" />
                지우기
              </button>
            </div>
            <ul className="flex flex-wrap gap-1.5">
              {recent.map((q) => (
                <li key={q}>
                  <button
                    type="button"
                    onClick={() => searchFor(q)}
                    className="border-border bg-card hover:bg-accent flex items-center gap-1 rounded-full border px-2.5 py-1 text-[13px]"
                  >
                    <Clock className="text-muted-foreground size-3" />
                    {q}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )
      ) : isPending ? (
        <LoadingText />
      ) : isError ? (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">
            검색하지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        </EmptyState>
      ) : results.length === 0 ? (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">검색 결과가 없어요.</p>
          <Button variant="outline" onClick={() => setManualOpen(true)}>
            <Pencil className="size-4" />
            직접 입력
          </Button>
        </EmptyState>
      ) : (
        <ul className="flex flex-col gap-2 pb-6">
          {results.map((place: PlaceSearchResult) => (
            <PlaceCard
              key={place.googlePlaceId}
              place={place}
              pending={createActivity.isPending}
              onAdd={(p) =>
                setTarget({
                  kind: "google",
                  googlePlaceId: p.googlePlaceId,
                  name: p.name,
                })
              }
            />
          ))}
        </ul>
      )}

      <PickDaySheet
        open={target !== null}
        onOpenChange={(open) => !open && setTarget(null)}
        placeName={target?.name ?? null}
        days={board?.days ?? []}
        onPick={add}
        pending={createActivity.isPending}
      />

      <BottomSheet
        open={manualOpen}
        onOpenChange={setManualOpen}
        title="직접 입력"
        description="검색으로 안 나오는 곳을 직접 만듭니다"
      >
        <form className="flex flex-col gap-3" onSubmit={submitManual}>
          <FormField label="장소 이름" htmlFor="manualPlaceName">
            <Input
              id="manualPlaceName"
              value={manualName}
              onChange={(e) => setManualName(e.target.value)}
              placeholder="숙소 근처 골목 카페"
              maxLength={200}
              autoFocus
            />
          </FormField>
          <FormField label="주소 (선택)" htmlFor="manualPlaceAddress">
            <Input
              id="manualPlaceAddress"
              value={manualAddress}
              onChange={(e) => setManualAddress(e.target.value)}
              maxLength={300}
            />
          </FormField>
          <div className="flex justify-end">
            <Button type="submit" disabled={!manualName.trim()}>
              만들기
            </Button>
          </div>
        </form>
      </BottomSheet>
    </div>
  );
}
