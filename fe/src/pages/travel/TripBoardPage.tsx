import {
  ArrowLeft,
  Map,
  MoreVertical,
  Plus,
  Search,
  Wrench,
} from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { Menu, MenuItem } from "@/components/ui/menu";
import type { Activity } from "@/features/travel/api/activities";
import { deleteTrip } from "@/features/travel/api/travel";
import { ActivityRow } from "@/features/travel/board/ActivityRow";
import { AddSheet } from "@/features/travel/board/AddSheet";
import { DayTabs } from "@/features/travel/board/DayTabs";
import {
  useCreateActivity,
  useDeleteActivity,
  useUpdateActivity,
} from "@/features/travel/hooks/useActivityMutations";
import { useBoard } from "@/features/travel/hooks/useBoard";
import { toast } from "@/shared/lib/toast";

/** `?day=` 값 — 0부터 시작하는 일차 인덱스, 또는 보관함. */
const ARCHIVE = "archive";

/**
 * S-04 일정 보드. 주 화면이다.
 *
 * <p><b>선택한 날짜는 URL이 소유한다</b>(`?day=0..N|archive`). 컴포넌트 상태로 들고 있으면
 * 새로고침·뒤로가기에서 1일차로 튕겨, 현지에서 앱을 다시 열 때마다 오늘을 다시 찾아야 한다.
 *
 * <p>드래그 정렬·스와이프·실행취소는 #1038에서 붙는다.
 */
export function TripBoardPage() {
  const { tripId: tripIdParam } = useParams();
  const tripId = Number(tripIdParam);
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const day = searchParams.get("day");
  const isArchive = day === ARCHIVE;
  const dayIndex = day !== null && !isArchive ? Number(day) : null;

  // 기본 조회. 날짜를 지정하지 않으면 서버가 고르고, 그 응답에 전체 날짜 탭이 들어 있다.
  // `?day=<인덱스>`를 날짜로 바꾸려면 이 목록이 먼저 있어야 한다.
  const { data: base, isPending } = useBoard(tripId, {});
  const requestedDate =
    dayIndex !== null ? base?.days[dayIndex]?.date : undefined;

  // 요청한 날짜가 서버 기본값과 같으면 기본 조회를 그대로 쓴다(추가 요청 없음).
  const needsOwnQuery =
    isArchive ||
    (requestedDate !== undefined && requestedDate !== base?.selectedDate);
  const { data: dayBoard } = useBoard(
    tripId,
    isArchive ? { archive: true } : { date: requestedDate },
    { enabled: needsOwnQuery },
  );
  const board = needsOwnQuery ? dayBoard : base;

  const [sheetOpen, setSheetOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Activity | null>(null);
  const [deletingTrip, setDeletingTrip] = useState(false);

  const createActivity = useCreateActivity(tripId);
  const updateActivity = useUpdateActivity(tripId);
  const removeActivity = useDeleteActivity(tripId);

  if (isPending || !board) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const selectedDate = isArchive ? null : board.selectedDate;
  const selectedIndex = board.days.findIndex((d) => d.date === selectedDate);

  const selectDay = (date: string) => {
    const index = board.days.findIndex((d) => d.date === date);
    setSearchParams({ day: String(index) }, { replace: true });
  };

  const selectArchive = () =>
    setSearchParams({ day: ARCHIVE }, { replace: true });

  const addActivity = async (input: {
    title: string;
    startTime: string | null;
  }) => {
    await createActivity.mutateAsync({
      title: input.title,
      activityDate: selectedDate,
      startTime: input.startTime,
    });
    setSheetOpen(false);
  };

  /** 보관함 일정을 지금 보고 있는 날짜로 옮긴다. */
  const pickFromArchive = async (activity: Activity) => {
    await updateActivity.mutateAsync({
      activityId: activity.id,
      body: { title: activity.title, activityDate: selectedDate },
    });
    setSheetOpen(false);
  };

  /** 일정을 보관함으로 내린다 — 지우는 게 아니라 날짜만 비운다. */
  const archiveActivity = async (activity: Activity) => {
    await updateActivity.mutateAsync({
      activityId: activity.id,
      body: {
        title: activity.title,
        activityDate: null,
        startTime: activity.startTime,
      },
    });
    toast("보관함으로 옮겼어요.", "success");
  };

  const confirmDeleteActivity = async () => {
    if (!pendingDelete) return;
    await removeActivity.mutateAsync(pendingDelete.id);
    setPendingDelete(null);
    toast("일정을 삭제했어요.", "success");
  };

  const removeTrip = async () => {
    await deleteTrip(tripId);
    setDeletingTrip(false);
    toast("여행을 삭제했어요.", "success");
    navigate("/travel/trips", { replace: true });
  };

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-3">
      <header className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="뒤로"
            onClick={() => navigate("/travel")}
          >
            <ArrowLeft className="size-4" />
          </Button>
          {/* 현지 시각·타임존 줄은 3단계(알림)와 함께 붙인다. */}
          <h1 className="text-heading truncate font-semibold">
            {board.trip.title}
          </h1>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {/* 지도는 2단계, 도구는 4단계. 자리를 미리 두되 누를 수 없게 한다. */}
          <Button variant="ghost" size="icon-sm" aria-label="지도" disabled>
            <Map className="size-4" />
          </Button>
          <Button variant="ghost" size="icon-sm" aria-label="도구" disabled>
            <Wrench className="size-4" />
          </Button>
          <Menu
            trigger={
              <Button variant="ghost" size="icon-sm" aria-label="여행 메뉴">
                <MoreVertical className="size-4" />
              </Button>
            }
          >
            <MenuItem onClick={() => navigate(`/travel/trips/${tripId}/edit`)}>
              여행 수정
            </MenuItem>
            <MenuItem disabled>알림 설정</MenuItem>
            <MenuItem
              variant="destructive"
              onClick={() => setDeletingTrip(true)}
            >
              여행 삭제
            </MenuItem>
          </Menu>
        </div>
      </header>

      <DayTabs
        days={board.days}
        archiveCount={board.archiveCount}
        selectedDate={selectedDate}
        onSelectDate={selectDay}
        onSelectArchive={selectArchive}
      />

      {board.activities.length > 0 ? (
        <ul className="flex flex-col">
          {board.activities.map((activity) => (
            <ActivityRow
              key={activity.id}
              activity={activity}
              inArchive={selectedDate === null}
              onArchive={(a) => void archiveActivity(a)}
              onDelete={setPendingDelete}
            />
          ))}
        </ul>
      ) : (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">
            {selectedDate === null
              ? "가고 싶은 곳을 미리 담아두세요"
              : "일정이 없어요"}
          </p>
          <Button variant="outline" disabled>
            <Search className="size-4" />
            장소 검색
          </Button>
        </EmptyState>
      )}

      <div className="flex justify-center py-2 pb-6">
        <Button
          variant="outline"
          size="icon-lg"
          aria-label="일정 추가"
          onClick={() => setSheetOpen(true)}
        >
          <Plus className="size-[18px]" />
        </Button>
      </div>

      <AddSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        tripId={tripId}
        targetDate={selectedDate}
        onCreate={(input) => void addActivity(input)}
        onPickFromArchive={(a) => void pickFromArchive(a)}
        pending={createActivity.isPending || updateActivity.isPending}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null);
        }}
        title="일정을 삭제할까요?"
        description={`"${pendingDelete?.title ?? ""}"이(가) 삭제됩니다.`}
        confirmLabel="삭제"
        destructive
        onConfirm={() => void confirmDeleteActivity()}
        pending={removeActivity.isPending}
      />

      <ConfirmDialog
        open={deletingTrip}
        onOpenChange={setDeletingTrip}
        title="여행을 삭제할까요?"
        description="일정과 기록이 함께 삭제됩니다. 되돌릴 수 없어요."
        confirmLabel="삭제"
        destructive
        onConfirm={() => void removeTrip()}
      />

      {/* 선택된 탭 위치를 스크린리더에도 알린다. */}
      <span className="sr-only" aria-live="polite">
        {selectedDate === null
          ? "보관함"
          : `${selectedIndex + 1}일차 ${selectedDate}`}
      </span>
    </div>
  );
}
