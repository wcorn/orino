import { ArrowLeft, Info } from "lucide-react";
import { type FormEvent, useEffect, useId, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import type { City } from "@/features/travel/api/places";
import {
  fetchShrinkPreview,
  type TripDetail,
  type TripWriteRequest,
} from "@/features/travel/api/travel";
import { useTrip } from "@/features/travel/hooks/useTrip";
import {
  useCreateTrip,
  useUpdateTrip,
} from "@/features/travel/hooks/useTripMutations";
import {
  CURRENCY_OPTIONS,
  DEFAULT_NOTIFY_MINUTES,
  NOTIFY_MINUTES_OPTIONS,
  TIMEZONE_OPTIONS,
} from "@/features/travel/lib/destinations";
import { DestinationSearch } from "@/features/travel/places/DestinationSearch";
import { toast } from "@/shared/lib/toast";

const TITLE_MAX = 50;

interface FormState {
  destinationName: string;
  title: string;
  startDate: string;
  endDate: string;
  timezone: string;
  currency: string;
  /** 목적지 검색으로만 채워진다. 직접 입력에는 좌표가 없다. */
  lat: number | null;
  lng: number | null;
  notifyMinutes: string;
}

const EMPTY: FormState = {
  destinationName: "",
  title: "",
  startDate: "",
  endDate: "",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  lat: null,
  lng: null,
  notifyMinutes: String(DEFAULT_NOTIFY_MINUTES),
};

/**
 * S-03 여행 생성·수정. 생성과 수정이 같은 폼을 쓴다(서버도 전체 수정이다).
 *
 * <p>입력 순서는 명세 고정 — 목적지 → 제목 → 기간 → 자동 지정 확인 → 기본 알림 시점.
 *
 * <p>목적지는 <b>검색으로 고른다</b>. 고르면 타임존·통화·좌표가 함께 정해진다.
 * 좌표가 특히 중요하다 — 장소 검색이 이 값으로 목적지 주변을 편향시킨다.
 *
 * <p>다만 검색은 유료 외부 API라 언제든 막힐 수 있고, 그때 여행을 아예 못 만들면 안 된다.
 * 직접 입력 + 타임존·통화 선택을 대체 경로로 남긴다.
 */
export function TripFormPage() {
  const { tripId } = useParams();
  const editingId = tripId ? Number(tripId) : null;
  const navigate = useNavigate();

  const { data: trip, isPending: loadingTrip } = useTrip(editingId);
  const createMutation = useCreateTrip();
  const updateMutation = useUpdateTrip(editingId ?? 0);

  const [form, setForm] = useState<FormState>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [shrinkCount, setShrinkCount] = useState<number | null>(null);
  const [checkingShrink, setCheckingShrink] = useState(false);
  // 수정 모드는 이미 목적지가 정해져 있다 — 다시 검색시키지 않고 저장된 값을 보여준다.
  const [manual, setManual] = useState(false);

  // 수정 모드는 서버 값으로 폼을 채운다.
  useEffect(() => {
    if (trip) setForm(toFormState(trip));
  }, [trip]);

  const timezoneLabelId = useId();
  const currencyLabelId = useId();
  const notifyLabelId = useId();

  const pending =
    createMutation.isPending || updateMutation.isPending || checkingShrink;

  if (editingId !== null && loadingTrip) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  /** 목적지를 고르면 타임존·통화·좌표가 한꺼번에 정해진다. 따로 고르게 하지 않는다. */
  const selectCity = (city: City) =>
    setForm((prev) => ({
      ...prev,
      destinationName: city.name,
      timezone: city.timezone,
      currency: city.currency,
      lat: city.lat,
      lng: city.lng,
    }));

  /** 저장 직전 검증. 서버도 같은 규칙을 보지만 왕복 없이 바로 알려주는 편이 낫다. */
  const validate = (): string | null => {
    if (!form.destinationName.trim()) return "목적지를 입력해 주세요.";
    if (form.title.trim().length > TITLE_MAX)
      return `제목은 ${TITLE_MAX}자를 넘을 수 없습니다.`;
    if (!form.startDate || !form.endDate) return "기간을 입력해 주세요.";
    if (form.endDate < form.startDate)
      return "종료일은 시작일보다 빠를 수 없습니다.";
    return null;
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const message = validate();
    if (message) {
      setError(message);
      return;
    }
    setError(null);

    // 기간이 줄면 잘리는 일정이 생긴다. 몇 개인지 먼저 물어보고 확인을 받는다.
    if (trip && isShrinking(trip, form)) {
      setCheckingShrink(true);
      try {
        const preview = await fetchShrinkPreview(
          trip.id,
          form.startDate,
          form.endDate,
        );
        if (preview.movedActivityCount > 0) {
          setShrinkCount(preview.movedActivityCount);
          return;
        }
      } catch {
        // 미리보기가 실패해도 저장을 막지 않는다 — 확인 없이 보내면 서버가 409로 되돌린다.
      } finally {
        setCheckingShrink(false);
      }
    }
    await save(false);
  };

  const save = async (confirmArchive: boolean) => {
    const body = toRequest(form, confirmArchive);
    try {
      const saved =
        editingId !== null
          ? await updateMutation.mutateAsync(body)
          : await createMutation.mutateAsync(body);
      toast(
        editingId !== null ? "여행을 수정했어요." : "여행을 만들었어요.",
        "success",
      );
      navigate(`/travel/trips/${saved.id}/board`, { replace: true });
    } catch {
      setShrinkCount(null);
      setError("저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  };

  return (
    <div className="mx-auto flex max-w-[520px] flex-col gap-6">
      <header className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="뒤로"
          onClick={() => navigate(-1)}
        >
          <ArrowLeft className="size-4" />
        </Button>
        <h1 className="text-heading font-semibold">
          {editingId !== null ? "여행 수정" : "여행 만들기"}
        </h1>
      </header>

      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        {/* 1. 목적지 — 검색으로 고르면 타임존·통화·좌표가 함께 정해진다. */}
        {manual ? (
          <div className="flex flex-col gap-2">
            <FormField label="목적지 도시" htmlFor="destinationName">
              <Input
                id="destinationName"
                value={form.destinationName}
                onChange={(e) => set("destinationName", e.target.value)}
                placeholder="도쿄"
                maxLength={100}
              />
            </FormField>
            <button
              type="button"
              onClick={() => setManual(false)}
              className="text-muted-foreground hover:text-foreground self-start text-xs underline"
            >
              검색으로 고르기
            </button>
          </div>
        ) : (
          <DestinationSearch
            value={form.destinationName}
            onSelect={selectCity}
            onFallback={() => setManual(true)}
          />
        )}

        {/* 2. 제목 — 비우면 목적지 이름으로 저장된다. placeholder로 그걸 보여준다. */}
        <FormField label="여행 제목" htmlFor="title">
          <Input
            id="title"
            value={form.title}
            onChange={(e) => set("title", e.target.value)}
            placeholder={
              form.destinationName || "비우면 목적지 이름으로 저장돼요"
            }
            maxLength={TITLE_MAX}
          />
        </FormField>

        {/* 3. 기간 */}
        <div className="flex gap-3">
          <FormField label="시작일" htmlFor="startDate" className="flex-1">
            <Input
              id="startDate"
              type="date"
              value={form.startDate}
              onChange={(e) => set("startDate", e.target.value)}
            />
          </FormField>
          <FormField label="종료일" htmlFor="endDate" className="flex-1">
            <Input
              id="endDate"
              type="date"
              value={form.endDate}
              onChange={(e) => set("endDate", e.target.value)}
            />
          </FormField>
        </div>

        {/* 검색으로 골랐으면 서버가 확정한 값이라 고를 것이 없다. 직접 입력일 때만 남는다. */}
        {manual && (
          <div className="flex gap-3">
            <FormField
              label="타임존"
              labelId={timezoneLabelId}
              className="min-w-0 flex-1"
            >
              <Select
                value={form.timezone}
                onValueChange={(v) => set("timezone", v)}
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
                value={form.currency}
                onValueChange={(v) => set("currency", v)}
                options={[...CURRENCY_OPTIONS]}
                ariaLabelledby={currencyLabelId}
              />
            </FormField>
          </div>
        )}

        {/* 4. 자동 지정 확인 — 검색으로 골랐다면 이 값은 서버가 정해 준 것이다. */}
        <Alert variant="info">
          <Info />
          <AlertTitle>여행 중에는 이 타임존을 씁니다</AlertTitle>
          <AlertDescription>
            {form.timezone} · {form.currency} — 일정은 이 타임존의 벽시계
            시각으로 표시됩니다.
          </AlertDescription>
        </Alert>

        {/* 5. 기본 알림 시점 */}
        <FormField label="기본 알림 시점" labelId={notifyLabelId}>
          <Select
            value={form.notifyMinutes}
            onValueChange={(v) => set("notifyMinutes", v)}
            options={[...NOTIFY_MINUTES_OPTIONS]}
            ariaLabelledby={notifyLabelId}
          />
        </FormField>

        {error && (
          <p role="alert" className="text-destructive text-sm">
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
            취소
          </Button>
          <Button type="submit" disabled={pending}>
            {editingId !== null ? "저장" : "만들기"}
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={shrinkCount !== null}
        onOpenChange={(open) => {
          if (!open) setShrinkCount(null);
        }}
        title="기간을 줄이면 일정이 이동합니다"
        description={`잘리는 날짜의 일정 ${shrinkCount ?? 0}개가 미배정 보관함으로 이동합니다.`}
        // 일정이 사라지는 게 아니라 옮겨지는 것이라 destructive가 아니다.
        confirmLabel="이동하고 저장"
        onConfirm={() => void save(true)}
        pending={updateMutation.isPending}
      />
    </div>
  );
}

/** 기간이 어느 쪽으로든 줄었는지 — 시작일을 늦춰도 앞쪽 일정이 잘린다. */
function isShrinking(trip: TripDetail, form: FormState): boolean {
  return form.startDate > trip.startDate || form.endDate < trip.endDate;
}

function toFormState(trip: TripDetail): FormState {
  return {
    destinationName: trip.destinationName,
    // 제목이 목적지명으로 자동 저장된 경우 폼을 비워 둔다 — 다시 저장해도 같은 값이 유지되고,
    // 사용자에겐 "안 정했다"는 원래 상태로 보인다.
    title: trip.title === trip.destinationName ? "" : trip.title,
    startDate: trip.startDate,
    endDate: trip.endDate,
    timezone: trip.timezone,
    currency: trip.currency,
    lat: trip.lat,
    lng: trip.lng,
    notifyMinutes: String(trip.defaultNotifyMinutes),
  };
}

function toRequest(form: FormState, confirmArchive: boolean): TripWriteRequest {
  return {
    title: form.title.trim() || undefined,
    destinationName: form.destinationName.trim(),
    startDate: form.startDate,
    endDate: form.endDate,
    timezone: form.timezone,
    currency: form.currency,
    lat: form.lat,
    lng: form.lng,
    defaultNotifyMinutes: Number(form.notifyMinutes),
    ...(confirmArchive ? { confirmArchive: true } : {}),
  };
}
