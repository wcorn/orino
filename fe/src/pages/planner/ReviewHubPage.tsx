import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";
import { Select, type SelectOption } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type {
  GradeFilter,
  ReviewScope,
  ReviewSummaryMaterial,
  UpcomingType,
  UpcomingWhen,
} from "@/features/review/api/reviewHub";
import { CompletedReviewRow } from "@/features/review/components/hub/CompletedReviewRow";
import {
  GRADE_OPTIONS,
  MATERIAL_ALL,
  TYPE_OPTIONS,
  WHEN_OPTIONS,
} from "@/features/review/components/hub/labels";
import {
  ReviewRail,
  type StatusTarget,
} from "@/features/review/components/hub/ReviewRail";
import { UpcomingReviewRow } from "@/features/review/components/hub/UpcomingReviewRow";
import { useCompletedReviews } from "@/features/review/hooks/useCompletedReviews";
import { useReviewSummary } from "@/features/review/hooks/useReviewSummary";
import { useUpcomingReviews } from "@/features/review/hooks/useUpcomingReviews";
import { useInfiniteScroll } from "@/shared/lib/useInfiniteScroll";

type Tab = "upcoming" | "completed";

export function ReviewHubPage() {
  const navigate = useNavigate();
  const { data: summary, isLoading, isError } = useReviewSummary();

  const [tab, setTab] = useState<Tab>("upcoming");
  const [scope, setScope] = useState<ReviewScope>("all");
  const [fMaterial, setFMaterial] = useState<string>(MATERIAL_ALL);
  const [fWhen, setFWhen] = useState<UpcomingWhen>("all");
  const [fType, setFType] = useState<UpcomingType>("all");
  const [fGrade, setFGrade] = useState<GradeFilter>("all");
  const [banner, setBanner] = useState<{
    name: string;
    due: number;
    id: number;
  } | null>(null);

  const materialId = fMaterial === MATERIAL_ALL ? undefined : Number(fMaterial);

  const upcomingQuery = useUpcomingReviews(
    { scope, materialId, when: fWhen, type: fType },
    tab === "upcoming",
  );
  const completedQuery = useCompletedReviews(
    { materialId, grade: fGrade === "all" ? undefined : fGrade },
    tab === "completed",
  );

  const upcomingItems = upcomingQuery.data?.pages.flatMap((p) => p.items) ?? [];
  const completedItems =
    completedQuery.data?.pages.flatMap((p) => p.items) ?? [];

  // totalCount는 첫 페이지에만 실린다. 필터가 바뀌면 쿼리 키가 바뀌어 첫 페이지부터 다시 받는다.
  const upcomingTotal = upcomingQuery.data?.pages[0]?.totalCount;
  const completedTotal = completedQuery.data?.pages[0]?.totalCount;

  const upcomingSentinel = useInfiniteScroll(
    () => upcomingQuery.fetchNextPage(),
    Boolean(upcomingQuery.hasNextPage) && !upcomingQuery.isFetchingNextPage,
  );
  const completedSentinel = useInfiniteScroll(
    () => completedQuery.fetchNextPage(),
    Boolean(completedQuery.hasNextPage) && !completedQuery.isFetchingNextPage,
  );

  if (isLoading) return <LoadingText />;
  if (isError || !summary) {
    return <FieldError>복습 현황을 불러오지 못했어요.</FieldError>;
  }

  const materialOptions: SelectOption<string>[] = [
    { value: MATERIAL_ALL, label: "전체 자료" },
    ...summary.materials.map((m) => ({ value: String(m.id), label: m.name })),
  ];

  const handleStatusSelect = (target: StatusTarget) => {
    if (target === "done") {
      setTab("completed");
      return;
    }
    setTab("upcoming");
    setScope(
      target === "now" ? "today" : target === "overdue" ? "overdue" : "all",
    );
  };

  const handleSelectMaterial = (m: ReviewSummaryMaterial) => {
    setFMaterial(String(m.id));
    setTab("upcoming");
    setBanner({ name: m.name, due: m.due, id: m.id });
  };

  const resetFilters = () => {
    setScope("all");
    setFMaterial(MATERIAL_ALL);
    setFWhen("all");
    setFType("all");
    setFGrade("all");
  };

  const startSession = (query: string) =>
    navigate(`/planner/reviews/session${query}`);

  const scopeChipLabel =
    scope === "overdue"
      ? "밀린 카드만 보는 중"
      : scope === "today"
        ? "오늘 예정만 보는 중"
        : null;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="복습"
        description="지금·밀림·앞으로·완료 현황을 한눈에 보고 시작하세요."
      />

      {banner && (
        <Alert
          variant="info"
          className="flex items-center justify-between gap-3"
        >
          <AlertDescription className="text-foreground">
            {banner.name} · 오늘 {banner.due}장 복습을 시작합니다
          </AlertDescription>
          <div className="col-start-2 flex shrink-0 gap-2">
            <Button
              size="sm"
              onClick={() => startSession(`?materialId=${banner.id}`)}
            >
              시작
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setBanner(null)}>
              닫기
            </Button>
          </div>
        </Alert>
      )}

      <div className="flex flex-col gap-4 min-[830px]:flex-row">
        <aside className="min-[830px]:w-72 min-[830px]:shrink-0">
          <ReviewRail
            summary={summary}
            activeTab={tab}
            scope={scope}
            activeMaterialId={materialId}
            onStatusSelect={handleStatusSelect}
            onStartAll={() => startSession("?scope=all")}
            onStartOverdue={() => startSession("?scope=overdue")}
            onSelectMaterial={handleSelectMaterial}
          />
        </aside>

        <main className="min-w-0 flex-1">
          <Tabs value={tab} onValueChange={(v) => setTab(v as Tab)}>
            <TabsList>
              {/* 전체 총계는 좌측 레일에 있다. 여기 숫자는 필터 기준 총 개수(FilterCount). */}
              <TabsTrigger value="upcoming">앞으로</TabsTrigger>
              <TabsTrigger value="completed">완료</TabsTrigger>
            </TabsList>

            <TabsContent value="upcoming" className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center gap-2">
                <Select
                  value={fMaterial}
                  onValueChange={setFMaterial}
                  options={materialOptions}
                  ariaLabelledby="filter-material-upcoming"
                />
                <span id="filter-material-upcoming" className="sr-only">
                  자료 필터
                </span>
                <Select
                  value={fWhen}
                  onValueChange={setFWhen}
                  options={WHEN_OPTIONS}
                />
                <Select
                  value={fType}
                  onValueChange={setFType}
                  options={TYPE_OPTIONS}
                />
                {scopeChipLabel && (
                  <Badge variant="outline" className="gap-1">
                    {scopeChipLabel}
                    <button
                      type="button"
                      aria-label="스코프 해제"
                      onClick={() => setScope("all")}
                      className="hover:text-foreground"
                    >
                      ✕
                    </button>
                  </Badge>
                )}
                <FilterCount total={upcomingTotal} />
              </div>

              <ReviewList
                isLoading={upcomingQuery.isLoading}
                isError={upcomingQuery.isError}
                isEmpty={upcomingItems.length === 0}
                emptyMessage="조건에 맞는 복습이 없어요."
                onReset={resetFilters}
                hint="스크롤하면 계속 불러옵니다"
                hasNextPage={Boolean(upcomingQuery.hasNextPage)}
                sentinelRef={upcomingSentinel}
              >
                {upcomingItems.map((item) => (
                  <UpcomingReviewRow key={item.id} item={item} />
                ))}
              </ReviewList>
            </TabsContent>

            <TabsContent value="completed" className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center gap-2">
                <Select
                  value={fMaterial}
                  onValueChange={setFMaterial}
                  options={materialOptions}
                  ariaLabelledby="filter-material-completed"
                />
                <span id="filter-material-completed" className="sr-only">
                  자료 필터
                </span>
                <Select
                  value={fGrade}
                  onValueChange={setFGrade}
                  options={GRADE_OPTIONS}
                />
                <FilterCount total={completedTotal} />
              </div>

              <ReviewList
                isLoading={completedQuery.isLoading}
                isError={completedQuery.isError}
                isEmpty={completedItems.length === 0}
                emptyMessage="조건에 맞는 완료 이력이 없어요."
                onReset={resetFilters}
                hint="최근 복습 순 · 스크롤하면 계속"
                hasNextPage={Boolean(completedQuery.hasNextPage)}
                sentinelRef={completedSentinel}
              >
                {completedItems.map((item) => (
                  <CompletedReviewRow key={item.id} item={item} />
                ))}
              </ReviewList>
            </TabsContent>
          </Tabs>
        </main>
      </div>
    </div>
  );
}

/** 필터 줄 오른쪽 끝의 "총 N개". 아직 못 받았으면 자리만 비워 둔다(레이아웃은 유지). */
function FilterCount({ total }: { total?: number }) {
  return (
    <p
      className="text-muted-foreground ml-auto text-sm tabular-nums"
      aria-live="polite"
    >
      {total === undefined ? "" : `총 ${total.toLocaleString()}개`}
    </p>
  );
}

interface ReviewListProps {
  isLoading: boolean;
  isError: boolean;
  isEmpty: boolean;
  emptyMessage: string;
  onReset: () => void;
  hint: string;
  hasNextPage: boolean;
  sentinelRef: React.RefObject<HTMLDivElement | null>;
  children: React.ReactNode;
}

function ReviewList({
  isLoading,
  isError,
  isEmpty,
  emptyMessage,
  onReset,
  hint,
  hasNextPage,
  sentinelRef,
  children,
}: ReviewListProps) {
  if (isLoading) return <LoadingText />;
  if (isError) return <FieldError>목록을 불러오지 못했어요.</FieldError>;
  if (isEmpty) {
    return (
      <EmptyState className="min-h-[30svh]">
        <p className="text-muted-foreground">{emptyMessage}</p>
        <Button variant="outline" onClick={onReset}>
          필터 초기화
        </Button>
      </EmptyState>
    );
  }
  return (
    <div className="flex flex-col gap-2">
      {children}
      <div ref={sentinelRef} aria-hidden />
      {hasNextPage && (
        <p className="text-muted-foreground py-2 text-center text-xs">{hint}</p>
      )}
    </div>
  );
}
