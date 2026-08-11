import {
  closestCenter,
  type CollisionDetection,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  pointerWithin,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import {
  ArrowLeft,
  Map as MapIcon,
  MapPin,
  MoreVertical,
  Plus,
  Search,
  StickyNote,
  Wrench,
} from "lucide-react";
import { Fragment, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { Menu, MenuItem } from "@/components/ui/menu";
import type {
  Activity,
  BoardDay,
  TravelTime,
} from "@/features/travel/api/activities";
// 삭제는 뮤테이션 훅이 아니라 raw 요청을 쓴다 — 화면을 떠난 뒤에 보낼 수도 있어서다.
import { deleteActivity as deleteActivityRequest } from "@/features/travel/api/activities";
import type { DayUpdateRequest } from "@/features/travel/api/days";
import type { Stay, StayWriteRequest } from "@/features/travel/api/stays";
import { stayOverlapOf } from "@/features/travel/api/stays";
import { deleteTrip } from "@/features/travel/api/travel";
import { ActivityRow } from "@/features/travel/board/ActivityRow";
import { AddSheet } from "@/features/travel/board/AddSheet";
import { BaseCitySheet } from "@/features/travel/board/BaseCitySheet";
import { DayTabs } from "@/features/travel/board/DayTabs";
import { DragModeBar } from "@/features/travel/board/DragModeBar";
import { LocalClockLine } from "@/features/travel/board/LocalClockLine";
import { OfflineBanner } from "@/features/travel/board/OfflineBanner";
import { usePendingActions } from "@/features/travel/board/pendingActions";
import { StayMoveRow } from "@/features/travel/board/StayMoveRow";
import { TransportSheet } from "@/features/travel/board/TransportSheet";
import { TravelTimeRow } from "@/features/travel/board/TravelTimeRow";
import { useUndoableAction } from "@/features/travel/board/useUndoableAction";
import {
  useCreateActivity,
  useReorderActivities,
  useUpdateActivity,
} from "@/features/travel/hooks/useActivityMutations";
import { useBoard } from "@/features/travel/hooks/useBoard";
import {
  useCreateStay,
  useDeleteStay,
  useStays,
  useUpdateStay,
} from "@/features/travel/hooks/useStays";
import { useUpdateDay } from "@/features/travel/hooks/useUpdateDay";
import {
  daysForPlace,
  groupArchiveByCity,
} from "@/features/travel/lib/archiveGroups";
import { directionsUrl } from "@/features/travel/lib/mapsLink";
import {
  badgeAboveList,
  badgeBelowList,
} from "@/features/travel/lib/stayBadge";
import { overlapMessage } from "@/features/travel/lib/stayForDay";
import { PickDaySheet } from "@/features/travel/places/PickDaySheet";
import { StayBadge } from "@/features/travel/stay/StayBadge";
import { StayFormSheet } from "@/features/travel/stay/StayFormSheet";
import { StaySheet } from "@/features/travel/stay/StaySheet";
import { toast } from "@/shared/lib/toast";
import { useOnline } from "@/shared/lib/useOnline";

/** `?day=` 값 — 0부터 시작하는 일차 인덱스, 또는 보관함. */
const ARCHIVE = "archive";

/**
 * 포인터가 들어간 대상을 먼저 본다.
 *
 * <p>기본 판정(사각형 겹침)은 끌고 있는 행의 큰 사각형을 기준으로 하는데, 날짜 칩은 작고
 * 목록 위쪽에 있어 손가락이 칩 위에 있어도 바로 아래 행이 더 많이 겹쳐 이긴다.
 * 칩에 떨어뜨리는 건 "손가락이 어디 있느냐"의 문제라 포인터를 우선한다.
 */
const collisionDetection: CollisionDetection = (args) => {
  const byPointer = pointerWithin(args);
  return byPointer.length > 0 ? byPointer : closestCenter(args);
};

/**
 * S-04 일정 보드. 주 화면이다.
 *
 * <p><b>선택한 날짜는 URL이 소유한다</b>(`?day=0..N|archive`). 컴포넌트 상태로 들고 있으면
 * 새로고침·뒤로가기에서 1일차로 튕겨, 현지에서 앱을 다시 열 때마다 오늘을 다시 찾아야 한다.
 *
 * <p>드래그는 행을 400ms 길게 눌러야 시작한다 — 목록을 세로로 스크롤하는 손짓과
 * 행을 집어 올리는 손짓을 구분하는 유일한 방법이다.
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
  const [deletingTrip, setDeletingTrip] = useState(false);
  /** 기준 도시 시트를 연 날짜. null이면 닫혀 있다. */
  const [cityDay, setCityDay] = useState<BoardDay | null>(null);
  /** 날짜를 고르는 중인 보관함 일정. null이면 시트가 닫혀 있다. */
  const [pickingDayFor, setPickingDayFor] = useState<Activity | null>(null);
  const [dragMode, setDragMode] = useState(false);
  const [openTravelTime, setOpenTravelTime] = useState<TravelTime | null>(null);
  /** 상세 시트를 연 숙소 id. null이면 닫혀 있다. */
  const [openStayId, setOpenStayId] = useState<number | null>(null);
  /** 등록·수정 폼. `editingStay`가 null이면 등록이다. */
  const [stayFormOpen, setStayFormOpen] = useState(false);
  const [editingStay, setEditingStay] = useState<Stay | null>(null);
  const [deletingStay, setDeletingStay] = useState<Stay | null>(null);
  /** 서버가 돌려준 겹침 안내. 폼이 열려 있는 동안만 유효하다. */
  const [stayError, setStayError] = useState<string | null>(null);
  // 오프라인은 조회 전용이다(§4.6). 편집을 막는 게 아니라 진입 자체를 없앤다.
  const online = useOnline();

  // 숙소는 여행 전체를 한 번에 읽는다 — 어느 날짜에 붙는지는 기간에서 파생한다.
  const { data: stays } = useStays(tripId);
  const createStay = useCreateStay(tripId);
  const updateStay = useUpdateStay(tripId);
  const removeStay = useDeleteStay(tripId);

  const createActivity = useCreateActivity(tripId);
  const updateActivity = useUpdateActivity(tripId);
  const updateDay = useUpdateDay(tripId);
  const reorder = useReorderActivities(tripId);
  const undoable = useUndoableAction(tripId);
  const pendingIds = usePendingActions((state) => state.pendingIds);

  // 드래그 모드 안에서만 정렬이 켜지므로(행의 `disabled`) 여기서는 지연을 두지 않는다.
  // 모드에 들어와 있다는 건 이미 "옮기려는 중"이라, 곧바로 잡히는 편이 자연스럽다.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  if (isPending || !board) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const selectedDate = isArchive ? null : board.selectedDate;
  const selectedIndex = board.days.findIndex((d) => d.date === selectedDate);
  // 실행취소를 기다리는 동안에는 이미 사라진 것처럼 보여야 한다(낙관적 반영).
  const activities = board.activities.filter((a) => !pendingIds.includes(a.id));
  /**
   * 구간을 <b>도착</b> 일정 id로 걸어 둔다 — 행을 그 일정 <b>바로 앞</b>에 그리기 위해서다.
   *
   * <p>출발 쪽에 붙이면 사이에 장소 없는 일정이 끼었을 때 "전망대 → 점심 28분"처럼 읽힌다.
   * 실제로는 점심을 건너뛴 전망대→저녁 구간이다. 도착 앞에 두면 "저녁까지 28분"이 되어
   * 건너뛴 일정이 있어도 가리키는 곳이 분명하다.
   */
  const travelTimesByTo = new Map(
    board.travelTimes.map((travelTime) => [
      travelTime.toActivityId,
      travelTime,
    ]),
  );

  /**
   * 이동시간 행을 탭했다.
   *
   * <p>도시를 넘는 이동이면 <b>이동수단 시트를 열지 않고</b> 곧바로 대중교통 길찾기로 나간다
   * (§3.4) — 서버가 계산하지 않은 구간이라 시트에 보여줄 도보/자동차가 애초에 없고, 물어볼
   * 이유도 없다. 실제로 타는 건 신칸센이고 그건 구글 지도가 답한다.
   */
  const openTravelTimeRow = (travelTime: TravelTime) => {
    if (!travelTime.crossCity) {
      setOpenTravelTime(travelTime);
      return;
    }
    const from = activities.find((a) => a.id === travelTime.fromActivityId);
    const to = activities.find((a) => a.id === travelTime.toActivityId);
    const url =
      from?.place && to?.place ? directionsUrl(from.place, to.place) : null;
    if (url === null) {
      // 좌표가 없으면 이동시간 행 자체가 없다. 그래도 열리면 아무 일도 안 일어난 것처럼
      // 두지 않는다 — 눌렀는데 반응이 없으면 고장으로 읽힌다.
      toast("길찾기를 열 수 없어요", "error");
      return;
    }
    window.open(url, "_blank", "noopener");
  };

  /** 보고 있는 날짜. 보관함을 보고 있으면 없다. */
  const selectedDay =
    board.days.find((day) => day.date === board.selectedDate) ?? null;
  /** 보고 있는 날짜의 기준 도시. 보관함에는 날짜가 없어 첫날로 떨어진다. */
  const selectedCity = (selectedDay ?? board.days[0])?.baseCity ?? null;

  /**
   * 이 여행에 등장하는 도시들. 기준 도시를 바꿀 때 가장 자주 고르는 후보라 시트 위쪽에
   * 그대로 올린다 — 도쿄↔닛코를 오가는 변경에 매번 검색을 시킬 이유가 없다.
   */
  const tripCities = [
    ...new Map(
      board.days
        .flatMap((day) => (day.baseCity ? [day.baseCity] : []))
        .map((city) => [city.placeId, city]),
    ).values(),
  ];

  /** 보관함은 순서가 아니라 도시로 읽는다. 보고 있지 않으면 계산할 이유가 없다. */
  const archiveGroups = isArchive
    ? groupArchiveByCity(activities, board.days)
    : [];

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

  /**
   * 보관함으로 내리기·삭제는 요청을 5초 미룬다. 화면에서는 즉시 사라지고,
   * 실행취소를 누르면 요청 자체가 나가지 않는다(서버에 복원 API를 두지 않는 이유).
   */
  const archiveActivity = (activity: Activity) =>
    undoable({
      activityId: activity.id,
      message: `"${activity.title}"을(를) 보관함으로 옮겼어요.`,
      run: () =>
        updateActivity.mutateAsync({
          activityId: activity.id,
          body: {
            title: activity.title,
            activityDate: null,
            startTime: activity.startTime,
          },
        }),
    });

  const removeActivityDeferred = (activity: Activity) =>
    undoable({
      activityId: activity.id,
      message: `"${activity.title}"을(를) 삭제했어요.`,
      run: () => deleteActivityRequest(activity.id),
    });

  /** 같은 날짜 안에서 두 행의 자리를 바꾼다(드래그·화살표 공통). */
  const moveWithin = (fromIndex: number, toIndex: number) => {
    if (toIndex < 0 || toIndex >= activities.length) return;
    const next = [...activities];
    const [moved] = next.splice(fromIndex, 1);
    next.splice(toIndex, 0, moved);
    reorder.mutate({
      date: selectedDate,
      activityIds: next.map((a) => a.id),
    });
  };

  /** 보관함 일정을 고른 날짜로 보낸다. 날짜를 고르지 않으면(보관함) 아무 일도 없다. */
  const pickDayFor = async (activity: Activity, date: string | null) => {
    setPickingDayFor(null);
    if (date === null) return;
    await moveToDay(activity, date);
  };

  /** 날짜 탭에 떨어뜨렸다 — 그 날짜의 맨 뒤로 보낸다. */
  const moveToDay = async (activity: Activity, target: string | null) => {
    if (target === selectedDate) return;
    await updateActivity.mutateAsync({
      activityId: activity.id,
      body: {
        title: activity.title,
        activityDate: target,
        startTime: activity.startTime,
      },
    });
    toast(
      target === null ? "보관함으로 옮겼어요." : "다른 날짜로 옮겼어요.",
      "success",
    );
  };

  /**
   * 기준 도시·도시 메모 저장. 도시가 바뀌면 구간이 다시 나뉘므로 탭 전체가 다시 그려진다
   * (응답이 기간 전체라 훅이 보드를 통째로 무효화한다).
   */
  const saveDay = async (body: DayUpdateRequest) => {
    if (!cityDay) return;
    if (Object.keys(body).length === 0) {
      setCityDay(null);
      return;
    }
    try {
      await updateDay.mutateAsync({ dayId: cityDay.dayId, body });
      setCityDay(null);
      toast(
        body.baseCityPlaceId || body.baseCityGooglePlaceId
          ? "기준 도시를 바꿨어요."
          : "도시 메모를 저장했어요.",
        "success",
      );
    } catch {
      toast("저장하지 못했어요.", "error");
    }
  };

  /** 행을 길게 눌렀다 — 드래그 모드로 들어간다. */
  const enterDragMode = () => {
    if (dragMode) return;
    setDragMode(true);
    // 모드에 들어왔다는 걸 손끝으로 알린다. 지원하지 않는 기기는 조용히 무시된다.
    navigator.vibrate?.(10);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over) return;

    const overId = String(over.id);
    if (overId.startsWith("day:")) {
      const target = overId.slice(4);
      const activity = activities.find((a) => a.id === active.id);
      if (activity) {
        void moveToDay(activity, target === ARCHIVE ? null : target);
      }
      return;
    }

    if (active.id === over.id) return;
    const fromIndex = activities.findIndex((a) => a.id === active.id);
    const toIndex = activities.findIndex((a) => a.id === over.id);
    if (fromIndex < 0 || toIndex < 0) return;
    moveWithin(fromIndex, toIndex);
    toast("순서 변경 · 이동시간과 알림을 다시 계산했어요.", "success");
  };

  const removeTrip = async () => {
    await deleteTrip(tripId);
    setDeletingTrip(false);
    toast("여행을 삭제했어요.", "success");
    navigate("/travel/trips", { replace: true });
  };

  /**
   * 배지가 아는 것은 숙소 id뿐이다(보드 응답이 이름·시각만 싣는다) — 상세는 숙소 목록에서
   * 찾는다. 오프라인이면 그 목록이 없어 열 수 없고, 왜 안 열리는지 말해 준다.
   */
  const openStay = (stayId: number) => {
    if (!stays?.some((stay) => stay.stayId === stayId)) {
      toast("숙소 정보를 불러오지 못했어요.", "error");
      return;
    }
    setOpenStayId(stayId);
  };

  const startAddStay = () => {
    setEditingStay(null);
    setStayError(null);
    setStayFormOpen(true);
  };

  const startEditStay = (stay: Stay) => {
    setOpenStayId(null);
    setEditingStay(stay);
    setStayError(null);
    setStayFormOpen(true);
  };

  /**
   * 겹침(409)은 <b>실패가 아니라 안내</b>다 — 시트를 닫지 않고 어느 숙소와 겹쳤는지 그 자리에
   * 보여준다. 닫아 버리면 사용자가 방금 친 것을 다시 쳐야 한다.
   */
  const saveStay = async (body: StayWriteRequest) => {
    try {
      if (editingStay) {
        await updateStay.mutateAsync({ stayId: editingStay.stayId, body });
      } else {
        await createStay.mutateAsync(body);
      }
      setStayFormOpen(false);
      setStayError(null);
      toast(
        editingStay ? "숙소를 수정했어요." : "숙소를 추가했어요.",
        "success",
      );
    } catch (error) {
      const conflict = stayOverlapOf(error);
      setStayError(
        conflict
          ? overlapMessage(conflict)
          : "저장하지 못했어요. 잠시 후 다시 시도해 주세요.",
      );
    }
  };

  const removeStayNow = async (stay: Stay) => {
    await removeStay.mutateAsync(stay.stayId);
    setDeletingStay(null);
    setOpenStayId(null);
    toast("숙소를 삭제했어요.", "success");
  };

  /**
   * 체크인·체크아웃을 일정으로 만든다 — <b>누를 때만</b>. 자동 생성하면 지워도 다음 조회에
   * 되살아나는 일정이 되고, 그 뒤로는 사용자가 지운 것을 앱이 되돌리는 셈이다.
   *
   * <p>만든 뒤로는 숙소와 아무 관계가 없는 보통 일정이다 — 숙소를 지워도 남는다.
   */
  const addStayActivities = async (stay: Stay) => {
    await Promise.all(
      [
        {
          title: `${stay.name} 체크인`,
          activityDate: stay.checkInDate,
          startTime: stay.checkInTime,
        },
        {
          title: `${stay.name} 체크아웃`,
          activityDate: stay.checkOutDate,
          startTime: stay.checkOutTime,
        },
      ].map((input) =>
        createActivity.mutateAsync({ ...input, placeId: stay.placeId }),
      ),
    );
    setOpenStayId(null);
    toast("체크인·체크아웃을 일정으로 추가했어요.", "success");
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
          <div className="min-w-0">
            <h1 className="text-heading truncate font-semibold">
              {board.trip.title}
            </h1>
            {/* 부제는 <b>보고 있는 날짜</b>의 기준 도시를 따른다 — 도시를 옮겨 다니면
                날짜 탭을 넘길 때마다 도시도 현지 시각도 바뀐다. */}
            {isArchive ? (
              <p className="text-caption text-muted-foreground">
                미배정 보관함
              </p>
            ) : (
              selectedCity && (
                <LocalClockLine
                  cityName={selectedCity.name}
                  timezone={selectedCity.timezone}
                  currency={selectedCity.currency}
                  recordMode={board.trip.recordMode}
                />
              )
            )}
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {/* 보던 날짜를 그대로 들고 간다 — 지도가 답하는 질문은 "오늘 이 순서가 말이 되나"다. */}
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="지도"
            onClick={() =>
              navigate(
                `/travel/trips/${tripId}/map${day === null ? "" : `?day=${day}`}`,
              )
            }
            disabled={isArchive || !online}
          >
            <MapIcon className="size-4" />
          </Button>
          {/* 환율·날씨는 그 여행에 매달린 값이라 어느 여행인지 들고 간다. */}
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="도구"
            disabled={!online}
            onClick={() => navigate(`/travel/tools?tripId=${tripId}`)}
          >
            <Wrench className="size-4" />
          </Button>
          <Menu
            trigger={
              <Button variant="ghost" size="icon-sm" aria-label="여행 메뉴">
                <MoreVertical className="size-4" />
              </Button>
            }
          >
            {/* 롱프레스는 손가락의 길이다. 마우스·키보드로도 같은 곳에 닿아야 한다. */}
            <MenuItem
              disabled={selectedDay === null || !online}
              onClick={() => setCityDay(selectedDay)}
            >
              기준 도시 변경
            </MenuItem>
            <MenuItem onClick={() => navigate(`/travel/trips/${tripId}/edit`)}>
              구간 수정
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

      <DndContext
        sensors={sensors}
        collisionDetection={collisionDetection}
        onDragEnd={handleDragEnd}
      >
        {!online && <OfflineBanner />}

        <DayTabs
          days={board.days}
          archiveCount={board.archiveCount}
          selectedDate={selectedDate}
          singleCity={board.trip.singleCity}
          onSelectDate={selectDay}
          onSelectArchive={selectArchive}
          onLongPressDay={setCityDay}
          // 드래그가 시작된 뒤에 등록하면 그 드래그의 충돌 판정 대상에 들어가지 못한다.
          // 드래그 중이 아닐 때는 아무 영향도 없으므로 항상 켜 둔다.
          droppable
        />

        {/* 도시 메모는 있을 때만 한 줄. 없는 날짜에 빈 자리를 남기지 않는다. */}
        {selectedDay?.cityMemo && (
          <p className="bg-muted flex items-center gap-2 rounded-lg px-3 py-2 text-[13px]">
            <StickyNote className="size-3.5 shrink-0" />
            {selectedDay.cityMemo}
          </p>
        )}

        {/* 숙소 배지 — 체크아웃이 먼저다(§3.5). 보관함에는 "그날 밤"이 없다. */}
        {!isArchive && (
          <StayBadge
            item={badgeAboveList(selectedDay)}
            onOpen={openStay}
            onAdd={startAddStay}
            offline={!online}
          />
        )}

        {/* 보관함은 도시별로 묶는다 — 그 도시 날짜를 짜는 동안 볼 것이 한 덩어리가 된다.
            순서가 없는 목록이라 드래그 정렬도 없다. */}
        {isArchive &&
          archiveGroups.map((group) => (
            <section key={group.key} className="flex flex-col">
              <h2 className="text-caption text-muted-foreground flex items-center gap-1.5 px-2 pt-2 font-semibold">
                <MapPin className="size-3" />
                {group.label}
              </h2>
              <ul className="flex flex-col">
                {group.activities.map((activity) => (
                  <ActivityRow
                    key={activity.id}
                    activity={activity}
                    inArchive
                    dragMode={false}
                    offline={!online}
                    canMoveUp={false}
                    canMoveDown={false}
                    onMoveUp={() => {}}
                    onMoveDown={() => {}}
                    onArchive={() => {}}
                    onPickDay={() => setPickingDayFor(activity)}
                    onDelete={() => removeActivityDeferred(activity)}
                    onEnterDragMode={() => {}}
                  />
                ))}
              </ul>
            </section>
          ))}

        {!isArchive && activities.length > 0 && (
          <SortableContext
            items={activities.map((a) => a.id)}
            strategy={verticalListSortingStrategy}
          >
            <ul className="flex flex-col">
              {activities.map((activity, index) => (
                <Fragment key={activity.id}>
                  {/* 드래그 중에는 감춘다 — 순서가 바뀌는 중이라 표시값이 곧 거짓이 된다. */}
                  {!dragMode && travelTimesByTo.get(activity.id) && (
                    <TravelTimeRow
                      travelTime={travelTimesByTo.get(activity.id)!}
                      onOpen={openTravelTimeRow}
                      offline={!online}
                    />
                  )}
                  <ActivityRow
                    activity={activity}
                    inArchive={selectedDate === null}
                    dragMode={dragMode}
                    offline={!online}
                    canMoveUp={index > 0}
                    canMoveDown={index < activities.length - 1}
                    onMoveUp={() => moveWithin(index, index - 1)}
                    onMoveDown={() => moveWithin(index, index + 1)}
                    onArchive={() => archiveActivity(activity)}
                    onDelete={() => removeActivityDeferred(activity)}
                    onEnterDragMode={enterDragMode}
                  />
                </Fragment>
              ))}
              {/* 마지막 일정 → 오늘 밤 숙소. 드래그 중에는 순서가 바뀌는 중이라 감춘다. */}
              {!dragMode && board.stayMove && (
                <StayMoveRow stayMove={board.stayMove} />
              )}
            </ul>
          </SortableContext>
        )}

        {/* 숙소를 옮기는 날에만 — 위 배지(체크아웃)와 다른 숙소일 때다. 같으면 소음이다. */}
        {!isArchive && badgeBelowList(selectedDay) && (
          <StayBadge
            item={badgeBelowList(selectedDay)}
            onOpen={openStay}
            onAdd={startAddStay}
            offline={!online}
            hideAdd
          />
        )}
      </DndContext>

      {dragMode && <DragModeBar onDone={() => setDragMode(false)} />}

      {activities.length === 0 && (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">
            {selectedDate === null
              ? "가고 싶은 곳을 미리 담아두세요"
              : "일정이 없어요"}
          </p>
          <Button
            variant="outline"
            disabled={!online}
            onClick={() => navigate(`/travel/trips/${tripId}/places`)}
          >
            <Search className="size-4" />
            장소 검색
          </Button>
        </EmptyState>
      )}

      {/* 드래그 모드에서는 추가 버튼을 감춘다 — 옮기는 중에 누를 일이 없고 오조작만 는다. */}
      {!dragMode && online && (
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
      )}

      <TransportSheet
        open={openTravelTime !== null}
        onOpenChange={(open) => !open && setOpenTravelTime(null)}
        tripId={tripId}
        travelTime={openTravelTime}
        activities={activities}
      />

      <AddSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        tripId={tripId}
        targetDate={selectedDate}
        onCreate={(input) => void addActivity(input)}
        onPickFromArchive={(a) => void pickFromArchive(a)}
        onSearchPlaces={() => navigate(`/travel/trips/${tripId}/places`)}
        pending={createActivity.isPending || updateActivity.isPending}
      />

      {/* 담기 시트는 S-06 검색 결과와 같은 것을 쓴다 — 담는 자리마다 다르게 생기면
          "어느 날에 담을까"라는 같은 질문을 두 번 배워야 한다. */}
      <PickDaySheet
        open={pickingDayFor !== null}
        onOpenChange={(open) => !open && setPickingDayFor(null)}
        placeName={pickingDayFor?.title ?? null}
        days={daysForPlace(board.days, pickingDayFor?.place?.cityPlaceRef)}
        onPick={(date) => pickingDayFor && void pickDayFor(pickingDayFor, date)}
        pending={updateActivity.isPending}
      />

      <BaseCitySheet
        day={cityDay}
        tripCities={tripCities}
        onOpenChange={(open) => !open && setCityDay(null)}
        onSubmit={(body) => void saveDay(body)}
        pending={updateDay.isPending}
      />

      <StaySheet
        stay={stays?.find((stay) => stay.stayId === openStayId) ?? null}
        onOpenChange={(open) => !open && setOpenStayId(null)}
        onEdit={startEditStay}
        onDelete={(stay) => {
          setOpenStayId(null);
          setDeletingStay(stay);
        }}
        onAddActivity={(stay) => void addStayActivities(stay)}
        offline={!online}
        addingActivity={createActivity.isPending}
      />

      <StayFormSheet
        open={stayFormOpen}
        stay={editingStay}
        tripStartDate={board.trip.startDate}
        tripEndDate={board.trip.endDate}
        stays={stays ?? []}
        onOpenChange={(open) => !open && setStayFormOpen(false)}
        onSubmit={saveStay}
        pending={createStay.isPending || updateStay.isPending}
        serverError={stayError}
      />

      <ConfirmDialog
        open={deletingStay !== null}
        onOpenChange={(open) => !open && setDeletingStay(null)}
        title="숙소를 삭제할까요?"
        description="이 숙소가 붙어 있던 날짜에서 모두 사라집니다."
        confirmLabel="삭제"
        destructive
        onConfirm={() => deletingStay && void removeStayNow(deletingStay)}
        pending={removeStay.isPending}
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
