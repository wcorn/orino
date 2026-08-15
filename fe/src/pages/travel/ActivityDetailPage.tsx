import {
  ArrowLeft,
  Clock,
  MapPin,
  Navigation,
  Phone,
  Trash2,
} from "lucide-react";
import {
  type FormEvent,
  lazy,
  Suspense,
  useEffect,
  useId,
  useState,
} from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  type Activity,
  deleteActivity as deleteActivityRequest,
} from "@/features/travel/api/activities";
import { useUndoableAction } from "@/features/travel/board/useUndoableAction";
import { useActivity } from "@/features/travel/hooks/useActivity";
import { useUpdateActivity } from "@/features/travel/hooks/useActivityMutations";
import { useBoard } from "@/features/travel/hooks/useBoard";
import { usePlaceDetail } from "@/features/travel/hooks/usePlaceDetail";
import { useTrip } from "@/features/travel/hooks/useTrip";
import { activityWriteBodyFrom } from "@/features/travel/lib/activityWriteBody";
import { cityOn } from "@/features/travel/lib/baseCity";
import { NOTIFY_MINUTES_OPTIONS } from "@/features/travel/lib/destinations";
import { placeDirectionsUrl } from "@/features/travel/lib/mapsLink";
import { todayOpeningHours } from "@/features/travel/lib/openingHours";
import {
  toTimeInputValue,
  toWallClockTime,
} from "@/features/travel/lib/tripClock";
import { dayChips, formatShortDate } from "@/features/travel/lib/tripStatus";
import { RecordSection } from "@/features/travel/record/RecordSection";
import { toast } from "@/shared/lib/toast";
import { useOnline } from "@/shared/lib/useOnline";

// leaflet은 무겁다 — 장소 없는 일정에서는 받지 않는다.
const PlacePreviewMap = lazy(() =>
  import("@/features/travel/map/PlacePreviewMap").then((m) => ({
    default: m.PlacePreviewMap,
  })),
);

const MEMO_MAX = 1000;
const URL_MAX = 500;
/** 날짜 Select에서 "보관함"을 나타내는 값. 빈 문자열은 Select가 미선택으로 본다. */
const ARCHIVE_VALUE = "archive";

interface FormState {
  title: string;
  /** 날짜 문자열 또는 `ARCHIVE_VALUE`. */
  day: string;
  /** `"HH:mm"` 또는 빈 문자열. Date로 파싱하지 않는다. */
  startTime: string;
  memo: string;
  url: string;
  notifyEnabled: boolean;
  /** 빈 문자열이면 여행 기본값을 따른다. */
  notifyMinutes: string;
  departureNotifyEnabled: boolean;
}

/**
 * S-07 일정 상세·편집.
 *
 * <p>계획·알림 영역은 <b>명시적 저장</b>이다(자동 저장 아님). 날짜·시각을 고치는 도중의
 * 중간 상태가 그대로 저장되면 보드의 순서와 알림이 사용자가 의도하지 않은 시점에 흔들린다.
 *
 * <p>기록 영역은 반대로 <b>자동 저장</b>이고 form 밖에 있다 — 현지에서 저장 버튼을 찾게
 * 하지 않고, 계획을 저장하지 않아도 기록만 남아야 한다. 여행 시작 전에는 아예 없다.
 */
export function ActivityDetailPage() {
  const { activityId: activityIdParam } = useParams();
  const activityId = Number(activityIdParam);
  const navigate = useNavigate();

  const { data: activity, isPending } = useActivity(activityId);
  const { data: placeDetail } = usePlaceDetail(activity?.place?.id ?? null);
  const { data: trip } = useTrip(activity?.tripId ?? null);
  /**
   * 그날의 보드. <b>날짜가 갖는 것들</b>이 여기에만 있다 — 기준 도시·타임존과, 이 일정으로
   * 들어오는 이동({@code moves})이다.
   *
   * <p>여행 상세({@code trip.timezone})로는 안 된다. 그 값은 <b>첫날</b> 기준 도시에서
   * 파생된 것이라, 도시를 옮긴 날짜에서 조용히 틀린 타임존을 말한다.
   *
   * <p>보드에서 들어오는 경로가 대부분이라 대개 캐시가 이미 따뜻하다.
   */
  const { data: board } = useBoard(
    activity?.tripId ?? 0,
    { date: activity?.activityDate ?? undefined },
    { enabled: activity !== undefined },
  );
  const updateActivity = useUpdateActivity(activity?.tripId ?? 0);
  const undoable = useUndoableAction(activity?.tripId ?? 0);

  const [form, setForm] = useState<FormState | null>(null);
  const dayLabelId = useId();
  const notifyMinutesId = useId();
  // 오프라인은 조회 전용이다(§4.6) — 저장·삭제 요청을 아예 보내지 않는다.
  const online = useOnline();

  useEffect(() => {
    if (activity) setForm(toFormState(activity));
  }, [activity]);

  if (isPending || !activity || !form) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  // 기록은 여행 시작일부터다. 상태는 서버가 여행 타임존으로 판정해 내려준 값이라
  // 기기 시간대로 다시 계산하지 않는다 — 저장 거부 규칙과 같은 기준이어야 한다.
  const tripStarted = trip !== undefined && trip.status !== "UPCOMING";
  const mapsUrl = activity.place ? placeDirectionsUrl(activity.place) : null;
  const openingToday = todayOpeningHours(placeDetail?.openingHours ?? null);

  /**
   * 이 일정이 속한 탭으로 돌아간다.
   *
   * <p>기본 탭으로 돌려보내면 방금 옮기거나 지운 일정이 보이지 않아, 반영됐는지도
   * 실행취소가 먹혔는지도 확인할 수 없다.
   */
  const boardPathFor = (day: string) => {
    const base = `/travel/trips/${activity.tripId}/board`;
    if (day === ARCHIVE_VALUE) return `${base}?day=archive`;
    const index = trip
      ? dayChips(trip.startDate, trip.endDate).findIndex((c) => c.date === day)
      : -1;
    return index >= 0 ? `${base}?day=${index}` : base;
  };
  const boardPath = boardPathFor(form.day);
  // 시각이 없으면 언제 보낼지 정할 수 없다(§1.2) — 서버도 같은 규칙이다.
  const hasStartTime = form.startTime !== "";
  /**
   * 출발 알림을 켤 수 있는가. <b>판정은 서버가 한다</b>({@code canDepartureNotify}, #1142) —
   * 직전에 장소 있는 일정이 있어야 한다. 화면이 다시 따지면 보드와 답이 갈린다.
   */
  const canNotifyDeparture =
    hasStartTime && activity.place !== null && activity.canDepartureNotify;
  /** 이 일정으로 <b>들어오는</b> 이동. 사용자가 적지 않았으면 값이 비어 있다(#1208). */
  const incoming =
    activity.activityDate !== null &&
    board?.selectedDate === activity.activityDate
      ? board.moves.find((m) => m.toActivityId === activity.id)
      : undefined;
  /**
   * 소요 시간을 아직 안 적은 구간인가. 출발 알림은 <b>적힌 분</b>으로만 서므로, 스위치를
   * 켜도 알림이 안 오는 이유가 여기에 있다 — 말해 주지 않으면 고장으로 읽힌다.
   */
  const durationMissing =
    canNotifyDeparture && incoming?.durationMinutes == null;
  /** 이 날짜의 기준 도시. 부제·알림 타임존이 쓴다. */
  const dayCity = cityOn(board?.days ?? [], activity.activityDate ?? "");
  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));

  /**
   * 1일차~N일차 + 보관함. 여행 기간을 알아야 만들 수 있어 상세를 함께 읽는다.
   *
   * <p>날짜마다 도시를 붙인다 — 다구간 여행에서 "4일차"만으로는 어디로 옮기는지 알 수 없다.
   * 아직 도시를 못 읽었으면 요일로 대신한다(빈 자리를 남기지 않는다).
   */
  const dayOptions = [
    ...(trip
      ? dayChips(trip.startDate, trip.endDate).map((chip) => {
          const city = cityOn(board?.days ?? [], chip.date);
          const where = city ? city.name : chip.weekday;
          return {
            value: chip.date,
            label: `${chip.dayIndex}일차 · ${where} (${formatShortDate(chip.date)})`,
          };
        })
      : []),
    { value: ARCHIVE_VALUE, label: "보관함 (미배정)" },
  ];

  /**
   * 헤더 부제 — 며칠째의 어느 도시인가. 보관함 일정에는 날짜가 없다.
   *
   * <p>아직 못 읽은 조각은 <b>빼고 잇는다.</b> 자리를 비워 두거나 `?일차`로 채우면 로딩 중
   * 화면이 고장난 것처럼 보인다.
   */
  const dayIndex = board?.days.find(
    (d) => d.date === activity.activityDate,
  )?.dayIndex;
  const subtitle =
    activity.activityDate === null
      ? "보관함 · 날짜 미정"
      : [
          dayIndex === undefined ? null : `${dayIndex}일차`,
          dayCity?.name,
          formatShortDate(activity.activityDate),
        ]
          .filter(Boolean)
          .join(" · ");

  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.title.trim()) return;
    try {
      await updateActivity.mutateAsync({
        activityId,
        // 폼에 없는 필드(장소)는 지금 값 그대로 되돌려 보낸다 — 수정은 전체 교체라
        // 빠뜨리면 서버가 지운다. 이 화면의 장소 블록은 읽기 전용이다(#1197).
        body: activityWriteBodyFrom(activity, {
          title: form.title.trim(),
          activityDate: form.day === ARCHIVE_VALUE ? null : form.day,
          // 문자열 그대로 보낸다. 시각이 없으면 null이고 그건 정상이다.
          startTime: toWallClockTime(form.startTime),
          memo: form.memo.trim() || null,
          url: form.url.trim() || null,
          // 저장하면 서버가 예약을 다시 짠다(§4.2).
          notifyEnabled: form.notifyEnabled,
          notifyMinutes: form.notifyMinutes ? Number(form.notifyMinutes) : null,
          departureNotifyEnabled: form.departureNotifyEnabled,
        }),
      });
      toast("일정을 저장했어요.", "success");
      // 옮겼다면 옮겨간 날짜의 탭으로 간다.
      navigate(boardPathFor(form.day));
    } catch {
      toast("저장하지 못했어요.", "error");
    }
  };

  /** 삭제는 보드로 돌아간 뒤에도 5초 안에 되돌릴 수 있다(보류함이 화면 밖에 있다). */
  const remove = () => {
    undoable({
      activityId,
      message: `"${activity.title}"을(를) 삭제했어요.`,
      run: () => deleteActivityRequest(activityId),
    });
    navigate(boardPath);
  };

  return (
    <div className="mx-auto flex max-w-[560px] flex-col gap-5">
      <header className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="뒤로"
          onClick={() => navigate(boardPath)}
        >
          <ArrowLeft className="size-4" />
        </Button>
        <div className="min-w-0 flex-1">
          <h1 className="text-heading truncate font-semibold">
            {activity.title}
          </h1>
          <p className="text-caption text-muted-foreground truncate">
            {subtitle}
          </p>
        </div>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="일정 삭제"
          disabled={!online}
          onClick={remove}
        >
          <Trash2 className="size-4" />
        </Button>
      </header>

      <form className="flex flex-col gap-4" onSubmit={save} noValidate>
        <h2 className="text-caption text-muted-foreground font-semibold">
          계획
        </h2>

        <FormField label="제목" htmlFor="title">
          <Input
            id="title"
            value={form.title}
            onChange={(e) => set("title", e.target.value)}
            maxLength={100}
          />
        </FormField>

        <div className="flex gap-3">
          <FormField
            label="날짜"
            labelId={dayLabelId}
            className="min-w-0 flex-1"
          >
            <Select
              value={form.day}
              onValueChange={(v) => set("day", v)}
              options={dayOptions}
              ariaLabelledby={dayLabelId}
            />
          </FormField>
          <FormField label="시작 시각" htmlFor="startTime" className="flex-1">
            <Input
              id="startTime"
              type="time"
              value={form.startTime}
              onChange={(e) => set("startTime", e.target.value)}
            />
          </FormField>
        </div>

        {activity.place && (
          <div className="border-border bg-card flex flex-col gap-2.5 rounded-xl border p-3">
            <div className="flex items-start gap-1.5">
              <MapPin className="text-primary mt-0.5 size-[15px] shrink-0" />
              <div className="min-w-0">
                <p className="text-[15px] font-medium">{activity.place.name}</p>
                {activity.place.address && (
                  <p className="text-muted-foreground text-xs">
                    {activity.place.address}
                  </p>
                )}
              </div>
            </div>

            {/* 영업시간·전화는 상세 조회에서 온다. 서버가 30일 캐시한다(§4.7). */}
            {openingToday && (
              <p className="text-muted-foreground flex items-center gap-1 text-xs">
                <Clock className="size-[11px] shrink-0" />
                {openingToday}
              </p>
            )}
            {placeDetail?.phone && (
              <a
                href={`tel:${placeDetail.phone}`}
                className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-xs"
              >
                <Phone className="size-[11px] shrink-0" />
                {placeDetail.phone}
              </a>
            )}

            {/* 좌표가 없으면(직접 입력) 지도도 길찾기도 없다. */}
            {activity.place.lat !== null && activity.place.lng !== null && (
              <Suspense
                fallback={<div className="bg-muted h-[120px] rounded-lg" />}
              >
                <PlacePreviewMap
                  lat={activity.place.lat}
                  lng={activity.place.lng}
                />
              </Suspense>
            )}

            {/* 앱 내 이동시간이 도보/자동차여도 딥링크는 항상 대중교통이다(§4.5). */}
            {mapsUrl && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="w-full"
                onClick={() => window.open(mapsUrl, "_blank", "noopener")}
              >
                <Navigation className="size-3.5" />
                구글 지도에서 길찾기 (대중교통)
              </Button>
            )}
          </div>
        )}

        {/*
          알림 영역(§S-07). 시각이 없으면 언제 보낼지 정할 수 없어 통째로 비활성이다 —
          서버도 같은 규칙으로 판정한다(§1.2).
        */}
        <section
          className={`flex flex-col gap-3 border-t pt-5 ${
            hasStartTime ? "" : "opacity-55"
          }`}
        >
          <h2 className="text-caption text-muted-foreground font-semibold">
            알림
          </h2>

          {!hasStartTime && (
            <p className="text-muted-foreground text-[13px]">
              시각을 입력하면 알림을 설정할 수 있어요.
            </p>
          )}

          <div className="flex items-center gap-3 border-b pb-3">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium">일정 알림</p>
              {/* 타임존을 함께 말한다(§9.4) — `09:00`이 어느 도시의 09:00인지가
                  도시를 옮기는 여행에서는 매번 달라진다. */}
              <p className="text-muted-foreground text-xs">
                {[
                  form.notifyMinutes
                    ? `시작 ${form.notifyMinutes}분 전`
                    : "여행 기본값",
                  dayCity?.timezone,
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </p>
            </div>
            <Select
              value={form.notifyMinutes}
              onValueChange={(value) => set("notifyMinutes", value)}
              options={[
                { value: "", label: "여행 기본값" },
                ...NOTIFY_MINUTES_OPTIONS,
              ]}
              ariaLabelledby={notifyMinutesId}
              disabled={!online || !hasStartTime || !form.notifyEnabled}
            />
            <span id={notifyMinutesId} className="sr-only">
              알림 시점
            </span>
            <Switch
              checked={form.notifyEnabled}
              onCheckedChange={(checked) => set("notifyEnabled", checked)}
              disabled={!online || !hasStartTime}
              aria-label="일정 알림"
            />
          </div>

          <div className="flex items-center gap-3">
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium">출발 알림</p>
              {/* 못 켜는 이유와 켜도 안 서는 이유를 구분해 말한다 — 스위치는 켰는데
                  알림이 안 오면, 이유를 모르는 사용자에게는 그냥 고장이다. */}
              <p className="text-muted-foreground text-xs">
                {!canNotifyDeparture
                  ? "시각과 이전 장소가 필요해요"
                  : durationMissing
                    ? "보드에서 이동 시간을 적으면 알림이 잡혀요"
                    : "시작시각 − 이동 시간 − 5분"}
              </p>
            </div>
            <Switch
              checked={form.departureNotifyEnabled}
              onCheckedChange={(checked) =>
                set("departureNotifyEnabled", checked)
              }
              disabled={!online || !canNotifyDeparture}
              aria-label="출발 알림"
            />
          </div>
        </section>

        <FormField label="메모" htmlFor="memo">
          <Textarea
            id="memo"
            rows={3}
            value={form.memo}
            onChange={(e) => set("memo", e.target.value)}
            maxLength={MEMO_MAX}
          />
        </FormField>

        <FormField label="링크" htmlFor="url">
          <Input
            id="url"
            value={form.url}
            onChange={(e) => set("url", e.target.value)}
            placeholder="https://"
            maxLength={URL_MAX}
          />
        </FormField>

        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="ghost"
            onClick={() => navigate(boardPath)}
          >
            취소
          </Button>
          <Button type="submit" disabled={updateActivity.isPending || !online}>
            저장
          </Button>
        </div>
      </form>

      {/*
        기록 영역(§S-07)은 여행 시작일 이후에만 존재한다 — 아직 겪지 않은 일에 평점을
        매길 수 없다. 계획 form 밖에 둔 것은 저장 경로가 다르기 때문이다(자동 저장,
        사진과도 분리된 요청). 안에 두면 Enter 한 번이 계획까지 저장한다.
      */}
      {tripStarted && (
        <RecordSection
          activityId={activityId}
          tripId={activity.tripId}
          log={activity.log}
          online={online}
        />
      )}
    </div>
  );
}

function toFormState(activity: Activity): FormState {
  return {
    title: activity.title,
    day: activity.activityDate ?? ARCHIVE_VALUE,
    startTime: toTimeInputValue(activity.startTime),
    memo: activity.memo ?? "",
    url: activity.url ?? "",
    notifyEnabled: activity.notifyEnabled,
    notifyMinutes:
      activity.notifyMinutes === null ? "" : String(activity.notifyMinutes),
    departureNotifyEnabled: activity.departureNotifyEnabled,
  };
}
