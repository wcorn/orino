import { MapPin, TriangleAlert } from "lucide-react";
import { useState } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { LoadingText } from "@/components/ui/loading-text";
import { Switch } from "@/components/ui/switch";
import type {
  PrepCategory,
  PrepItemView,
  PrepPatchRequest,
} from "@/features/travel/api/prep";
import { isTripNotFound } from "@/features/travel/api/travel";
import { OfflineBanner } from "@/features/travel/board/OfflineBanner";
import { usePendingPrepActions } from "@/features/travel/board/pendingActions";
import {
  useCreatePrepItem,
  useDeletePrepItem,
  usePrep,
  useUpdatePrepItem,
} from "@/features/travel/hooks/usePrep";
import { useTrip } from "@/features/travel/hooks/useTrip";
import { PREP_DEFAULT_OPEN } from "@/features/travel/prep/categories";
import { PrepAddBar } from "@/features/travel/prep/PrepAddBar";
import { PrepCategoryCard } from "@/features/travel/prep/PrepCategoryCard";
import { PrepItemSheet } from "@/features/travel/prep/PrepItemSheet";
import { usePrepUndo } from "@/features/travel/prep/usePrepUndo";
import { TripBreadcrumb } from "@/features/travel/trip/TripBreadcrumb";
import { useOnline } from "@/shared/lib/useOnline";

/**
 * 준비 `/travel/trips/:tripId/prep` (S-10).
 *
 * <p>이 화면이 답하는 것은 셋이다 — <b>출발까지 아직 안 한 게 뭔가 · 가방에 뭘 넣나 ·
 * 지금 안 하면 늦는 게 있나</b>. 출발 전날 밤에 이것만 열면 되게 하는 것이 목표다(§9).
 *
 * <p><b>진행률·기한 지남 개수는 서버가 준 값을 그대로 쓴다.</b> 화면이 다시 세지 않는다 —
 * 기한 지남은 「첫날 기준 도시의 오늘」로 판정하는데 그 시각이 브라우저에 없고, 각자 세면
 * 사이드바 배지에 1이 떠 있는데 화면에는 아무 줄도 빨갛지 않은 상태가 생긴다.
 */
export function TripPrepPage() {
  const { tripId: tripIdParam } = useParams();
  const tripId = Number(tripIdParam);

  const [searchParams, setSearchParams] = useSearchParams();
  // 완료 숨기기는 URL이 갖는다(§10.7) — 새로고침해도 보던 상태 그대로 열린다.
  const hideDone = searchParams.get("hideDone") === "1";

  // 접힘은 URL에 넣지 않는다. 분류가 넷이라 파라미터가 화면보다 커진다.
  const [openCategories, setOpenCategories] =
    useState<PrepCategory[]>(PREP_DEFAULT_OPEN);
  /** 방금 적은 분류. 다음 항목이 이걸 이어받는다(§13). */
  const [addCategory, setAddCategory] = useState<PrepCategory>("TODO");
  const [editing, setEditing] = useState<PrepItemView | null>(null);

  // 오프라인은 조회 전용이다. 큐잉하지 않는다 — 예외를 하나 열면 충돌 해소가 설계에
  // 들어온다(D-33).
  const online = useOnline();

  const { data, isPending, isError, error } = usePrep(tripId);
  // 브레드크럼이 쓰는 이름 하나. 다른 여행 화면이 이미 받아 둔 캐시를 그대로 탄다.
  const { data: trip } = useTrip(tripId);
  const createItem = useCreatePrepItem(tripId);
  const updateItem = useUpdatePrepItem(tripId);
  const deleteItem = useDeletePrepItem(tripId);
  const undoable = usePrepUndo(tripId);
  const pendingIds = usePendingPrepActions((state) => state.pendingIds);

  /*
    없는 여행이면 「불러오지 못했어요」가 아니라 고르게 한다 — 지운 직후이거나 남의
    링크를 받은 경우다. `replace`로 바꿔 죽은 URL을 뒤로 가기에 남기지 않는다.
  */
  if (isError && isTripNotFound(error)) {
    return <Navigate to="/travel/prep" replace />;
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-[720px]">
        <Alert variant="destructive">준비 목록을 불러오지 못했어요.</Alert>
      </div>
    );
  }

  if (isPending || !data) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const openCategory = (category: PrepCategory) =>
    setOpenCategories((prev) =>
      prev.includes(category) ? prev : [...prev, category],
    );

  const add = (title: string) => {
    createItem.mutate(
      { category: addCategory, title },
      // 어느 분류로 들어갔는지는 서버가 정한다. 그 분류를 펼쳐야 방금 적은 줄이 보인다.
      { onSuccess: (result) => openCategory(result.category) },
    );
  };

  const save = (itemId: number, body: PrepPatchRequest) => {
    updateItem.mutate({ itemId, body });
    if (body.category) {
      // 분류를 옮겼으면 옮겨 간 곳을 펼친다 — 안 그러면 저장하자마자 항목이 사라져 보인다.
      setAddCategory(body.category);
      openCategory(body.category);
    }
  };

  const remove = (item: PrepItemView) => {
    undoable({
      itemId: item.id,
      message: `「${item.title}」을(를) 지웠어요`,
      run: () => deleteItem.mutateAsync(item.id),
    });
    setEditing(null);
  };

  /**
   * 실행취소를 기다리는 동안에는 이미 사라진 것처럼 보여야 한다(낙관적 반영).
   *
   * <p>다만 <b>집계는 손대지 않는다.</b> 5초 뒤 요청이 나가면 서버가 다시 세어 주고, 그
   * 사이 화면이 스스로 −1 하면 되돌렸을 때 되돌려 놓을 값을 또 기억해야 한다.
   */
  const groups = data.groups.map((group) => ({
    ...group,
    items: group.items.filter((item) => !pendingIds.includes(item.id)),
  }));

  const remaining = data.total - data.done;
  const donePercent = data.total === 0 ? 0 : (data.done / data.total) * 100;

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-5">
      <TripBreadcrumb tripId={tripId} tripTitle={trip?.title} current="준비" />
      <PageHeader
        title="준비"
        description={`출발까지 ${data.dday}일`}
        actions={
          <div className="flex items-center gap-2">
            <label
              htmlFor="hide-done"
              className="text-muted-foreground text-[13px]"
            >
              완료 숨기기
            </label>
            <Switch
              id="hide-done"
              checked={hideDone}
              aria-label="완료 숨기기"
              onCheckedChange={(checked) =>
                setSearchParams(checked ? { hideDone: "1" } : {}, {
                  replace: true,
                })
              }
            />
            <span className="text-title text-primary font-semibold tabular-nums">
              D-{data.dday}
            </span>
          </div>
        }
      />

      {!online && <OfflineBanner what="준비" />}

      {/*
        새 여행의 준비 목록은 <b>항상 비어서 시작한다</b> — 템플릿도 「지난 여행에서
        가져오기」도 만들지 않기로 했기 때문이다(확정 명세 §15). 그 이유를 화면이 말해
        주지 않으면 빈 목록이 버그로 읽힌다(D-40).
      */}
      <p className="bg-accent text-accent-foreground flex items-start gap-2 rounded-lg px-3 py-2.5 text-[13px]">
        <MapPin className="mt-px size-[15px] shrink-0" />
        준비 목록은 여행마다 따로입니다. 사이드바에서 여행을 바꾸면 이 화면도 그
        여행의 목록으로 바뀝니다.
      </p>

      <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
        <div className="flex items-baseline justify-between">
          <h2 className="text-sm font-semibold">전체 진행률</h2>
          <p className="text-[22px]/[1.15] font-semibold tabular-nums">
            {data.done}/{data.total}
          </p>
        </div>

        <div
          className="bg-muted h-2.5 overflow-hidden rounded-full"
          role="img"
          aria-label={`${data.total}개 중 ${data.done}개 완료`}
        >
          <div
            className="bg-primary h-full rounded-full"
            style={{ width: `${donePercent}%` }}
          />
        </div>

        <div className="flex items-center gap-2 text-[13px]">
          <span className="text-muted-foreground">남은 {remaining}개</span>
          {/* 기한 지남에 「무시」를 두지 않는다. 눈에 거슬리는 게 목적이다(§13). */}
          {data.overdueCount > 0 && (
            <span className="text-destructive flex items-center gap-1 font-semibold">
              <TriangleAlert className="size-3.5" />
              기한 지난 것 {data.overdueCount}개
            </span>
          )}
        </div>
      </section>

      <div className="flex flex-col gap-3.5">
        {groups.map((group) => (
          <PrepCategoryCard
            key={group.category}
            group={group}
            open={openCategories.includes(group.category)}
            onToggleOpen={() =>
              setOpenCategories((prev) =>
                prev.includes(group.category)
                  ? prev.filter((c) => c !== group.category)
                  : [...prev, group.category],
              )
            }
            hideDone={hideDone}
            offline={!online}
            onToggleItem={(item, done) =>
              updateItem.mutate({ itemId: item.id, body: { done } })
            }
            onOpenItem={setEditing}
            onDeleteItem={remove}
          />
        ))}
      </div>

      <PrepAddBar
        category={addCategory}
        onCategoryChange={setAddCategory}
        offline={!online}
        onAdd={add}
      />

      <PrepItemSheet
        item={editing}
        category={
          editing
            ? (groups.find((g) => g.items.some((i) => i.id === editing.id))
                ?.category ?? null)
            : null
        }
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
        onSave={save}
        onDelete={remove}
      />
    </div>
  );
}
