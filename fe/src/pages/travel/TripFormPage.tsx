import { ArrowLeft, Info, Plus } from "lucide-react";
import { type FormEvent, Fragment, useEffect, useId, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import { type City, createManualPlace } from "@/features/travel/api/places";
import {
  fetchCityLegs,
  fetchShrinkPreview,
  type ShrinkPreview,
  type TripDetail,
  type TripLegRequest,
  type TripWriteRequest,
} from "@/features/travel/api/travel";
import { useTrip } from "@/features/travel/hooks/useTrip";
import {
  useCreateTrip,
  useUpdateTrip,
} from "@/features/travel/hooks/useTripMutations";
import {
  DEFAULT_NOTIFY_MINUTES,
  NOTIFY_MINUTES_OPTIONS,
} from "@/features/travel/lib/destinations";
import { planLegDates } from "@/features/travel/lib/legPlan";
import { CitySheet } from "@/features/travel/trip/CitySheet";
import {
  distinctTimezones,
  type LegDraft,
  legFromCity,
  legFromManualCity,
  legFromSaved,
} from "@/features/travel/trip/legDraft";
import { LegMoveRow } from "@/features/travel/trip/LegMoveRow";
import { LegRow } from "@/features/travel/trip/LegRow";
import { LegSumBar } from "@/features/travel/trip/LegSumBar";
import { toast } from "@/shared/lib/toast";

const TITLE_MAX = 50;

interface FormState {
  title: string;
  startDate: string;
  endDate: string;
  legs: LegDraft[];
  notifyMinutes: string;
}

const EMPTY: FormState = {
  title: "",
  startDate: "",
  endDate: "",
  legs: [],
  notifyMinutes: String(DEFAULT_NOTIFY_MINUTES),
};

/**
 * S-03 여행 생성·수정. 생성과 수정이 같은 폼을 쓴다(서버도 전체 수정이다).
 *
 * <p>입력 순서는 명세 고정 — 제목 → 기간 → 구간 → 타임존 안내 → 기본 알림 시점.
 *
 * <p><b>v2.1 — 목적지 하나가 아니라 구간 목록을 받는다.</b> 여행은 타임존을 갖지 않고
 * 날짜마다 기준 도시가 붙으므로, 화면이 정하는 것은 "어느 도시에 며칠"까지다. 날짜로 펴는
 * 일은 서버가 한다.
 *
 * <p>합계와 기간이 어긋나도 <b>저장을 막지 않는다.</b> 여행을 짜는 중간 상태가 대부분
 * 불일치라, 막으면 도시를 하나 추가할 때마다 기간을 먼저 늘려야 한다.
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
  const [shrink, setShrink] = useState<ShrinkPreview | null>(null);
  const [checkingShrink, setCheckingShrink] = useState(false);
  /** 도시 시트를 연 구간. null이면 닫혀 있다. */
  const [pickingKey, setPickingKey] = useState<string | null>(null);

  const notifyLabelId = useId();

  // 수정 모드는 서버 값으로 폼을 채운다. 구간은 저장된 것이 아니라 날짜에서 파생한 값이라
  // 따로 받아 온다(#1122) — 그래야 날짜와 어긋날 수가 없다.
  useEffect(() => {
    if (!trip) return;
    let alive = true;
    setForm(toFormState(trip));
    void fetchCityLegs(trip.id)
      .then((legs) => {
        if (!alive || legs.length === 0) return;
        setForm((prev) => ({ ...prev, legs: legs.map(legFromSaved) }));
      })
      .catch(() => {
        // 구간을 못 받아도 제목·기간은 고칠 수 있어야 한다. 구간을 비운 채로 두면
        // 저장 시 서버가 도시 배치를 건드리지 않는다(legs 생략).
      });
    return () => {
      alive = false;
    };
  }, [trip]);

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

  const plan = planLegDates(
    form.startDate,
    form.endDate,
    form.legs.map((leg) => leg.days),
  );
  const zones = distinctTimezones(form.legs);

  const updateLeg = (key: string, patch: Partial<LegDraft>) =>
    setForm((prev) => ({
      ...prev,
      legs: prev.legs.map((leg) =>
        leg.key === key ? { ...leg, ...patch } : leg,
      ),
    }));

  const addLeg = (leg: LegDraft) =>
    setForm((prev) => ({ ...prev, legs: [...prev.legs, leg] }));

  const removeLeg = (key: string) =>
    setForm((prev) => ({
      ...prev,
      legs: prev.legs.filter((leg) => leg.key !== key),
    }));

  const moveLeg = (index: number, direction: -1 | 1) =>
    setForm((prev) => {
      const legs = [...prev.legs];
      const target = index + direction;
      if (target < 0 || target >= legs.length) return prev;
      [legs[index], legs[target]] = [legs[target], legs[index]];
      return { ...prev, legs };
    });

  /** 시트에서 고른 도시를 반영한다. 열려 있던 자리가 없으면 새 구간으로 붙인다. */
  const applyCity = (city: City) => {
    if (pickingKey === null) return;
    if (pickingKey === NEW_LEG) {
      addLeg(legFromCity(city));
      return;
    }
    updateLeg(pickingKey, {
      cityName: city.name,
      cityGooglePlaceId: city.googlePlaceId,
      cityPlaceId: null,
      timezone: city.timezone,
      currency: city.currency,
      lat: city.lat,
      lng: city.lng,
    });
  };

  const applyManualCity = (
    cityName: string,
    timezone: string,
    currency: string,
  ) => {
    if (pickingKey === null) return;
    if (pickingKey === NEW_LEG) {
      addLeg(legFromManualCity(cityName, timezone, currency));
      return;
    }
    updateLeg(pickingKey, {
      cityName,
      cityGooglePlaceId: null,
      cityPlaceId: null,
      timezone,
      currency,
      lat: null,
      lng: null,
    });
  };

  /** 저장 직전 검증. 서버도 같은 규칙을 보지만 왕복 없이 바로 알려주는 편이 낫다. */
  const validate = (): string | null => {
    if (!form.title.trim()) return "여행 제목을 입력해 주세요.";
    if (form.title.trim().length > TITLE_MAX)
      return `제목은 ${TITLE_MAX}자를 넘을 수 없습니다.`;
    if (!form.startDate || !form.endDate) return "기간을 입력해 주세요.";
    if (form.endDate < form.startDate)
      return "종료일은 시작일보다 빠를 수 없습니다.";
    if (editingId === null && form.legs.length === 0)
      return "구간을 하나 이상 추가해 주세요.";
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

    // 기간이 줄면 잘리는 일정·숙소가 생긴다. 무엇이 밀려나는지 먼저 물어보고 확인을 받는다.
    if (trip && isShrinking(trip, form)) {
      setCheckingShrink(true);
      try {
        const preview = await fetchShrinkPreview(
          trip.id,
          form.startDate,
          form.endDate,
        );
        if (
          preview.movedActivityCount > 0 ||
          preview.shrunkStayCount > 0 ||
          preview.removedStayCount > 0
        ) {
          setShrink(preview);
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
    try {
      const body = await toRequest(form, editingId !== null, confirmArchive);
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
      setShrink(null);
      setError("저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  };

  return (
    <div className="mx-auto flex max-w-[560px] flex-col gap-6">
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
        {/* 1. 제목 — 필수다. 목적지가 여행에 없으니 자동으로 채울 이름도 없다. */}
        <FormField label="여행 제목" htmlFor="title">
          <Input
            id="title"
            value={form.title}
            onChange={(e) => set("title", e.target.value)}
            placeholder="일본 9박 10일"
            maxLength={TITLE_MAX}
          />
        </FormField>

        {/* 2. 기간 */}
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

        {/* 3. 구간 — 도시 + 일수. 순서가 곧 방문 순서다. */}
        <section className="flex flex-col gap-2" aria-labelledby="legsLabel">
          <h2 id="legsLabel" className="text-label font-medium">
            구간
          </h2>
          {form.legs.length > 0 && (
            <ul className="flex flex-col gap-2">
              {form.legs.map((leg, index) => {
                // 이 구간이 끝나고 다음 구간이 시작하는 날 = 이동일. 다음 구간이 기간을
                // 넘겨 잘렸으면(dates가 null) 이동 자체가 일어나지 않는다.
                const next = form.legs[index + 1];
                const moveDate = plan.dates[index + 1]?.startDate;
                return (
                  <Fragment key={leg.key}>
                    <LegRow
                      index={index}
                      cityName={leg.cityName}
                      days={leg.days}
                      dates={plan.dates[index]}
                      onPickCity={() => setPickingKey(leg.key)}
                      onChangeDays={(days) =>
                        updateLeg(leg.key, { days: Math.max(1, days) })
                      }
                      onMove={(direction) => moveLeg(index, direction)}
                      onRemove={() => removeLeg(leg.key)}
                      canMoveUp={index > 0}
                      canMoveDown={index < form.legs.length - 1}
                      canRemove={form.legs.length > 1}
                    />
                    {next && moveDate && leg.cityName && next.cityName && (
                      <LegMoveRow
                        date={moveDate}
                        fromCity={leg.cityName}
                        toCity={next.cityName}
                      />
                    )}
                  </Fragment>
                );
              })}
            </ul>
          )}

          <Button
            type="button"
            variant="outline"
            className="border-dashed"
            onClick={() => setPickingKey(NEW_LEG)}
          >
            <Plus className="size-4" />
            구간 추가
          </Button>

          {form.legs.length > 0 && <LegSumBar plan={plan} />}
        </section>

        {/* 4. 타임존 안내 — 값의 주인이 여행이 아니라 날짜라는 것을 말한다. */}
        <Alert variant="info">
          <Info />
          <AlertTitle>
            {zones.length > 1
              ? `타임존이 ${zones.length}개예요`
              : "타임존과 통화는 날짜마다 정해져요"}
          </AlertTitle>
          <AlertDescription>
            {zones.length > 0
              ? `${zones.join(" / ")} — 각 날짜의 기준 도시에서 자동으로 파생되고, 일정 시각은 그 도시의 벽시계 시각으로 표시됩니다.`
              : "구간을 추가하면 그 도시의 타임존과 통화가 정해집니다."}
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

      <CitySheet
        open={pickingKey !== null}
        onOpenChange={(open) => !open && setPickingKey(null)}
        onSelect={applyCity}
        onSelectManual={applyManualCity}
      />

      <ConfirmDialog
        open={shrink !== null}
        onOpenChange={(open) => !open && setShrink(null)}
        title="기간을 줄이면 일정이 이동합니다"
        description={describeShrink(shrink)}
        confirmLabel="이동하고 저장"
        onConfirm={() => {
          setShrink(null);
          void save(true);
        }}
      />
    </div>
  );
}

/** 새 구간을 만들려고 시트를 연 상태. 구간 키와 섞이지 않는 값이어야 한다. */
const NEW_LEG = "__new__";

/** 기간이 어느 쪽으로든 줄었는지 — 시작일을 늦춰도 앞쪽 일정이 잘린다. */
function isShrinking(trip: TripDetail, form: FormState): boolean {
  return form.startDate > trip.startDate || form.endDate < trip.endDate;
}

/** 무엇이 밀려나는지 그대로 말한다 — 숫자를 숨기면 확인의 뜻이 없다. */
function describeShrink(preview: ShrinkPreview | null): string {
  if (!preview) return "";
  const parts: string[] = [];
  if (preview.movedActivityCount > 0) {
    parts.push(
      `잘리는 날짜의 일정 ${preview.movedActivityCount}개가 미배정 보관함으로 이동`,
    );
  }
  if (preview.shrunkStayCount > 0) {
    parts.push(`걸쳐 있던 숙소 ${preview.shrunkStayCount}곳은 기간이 줄어듦`);
  }
  if (preview.removedStayCount > 0) {
    parts.push(`묵는 밤이 없어진 숙소 ${preview.removedStayCount}곳은 삭제`);
  }
  return `${parts.join(" · ")}합니다.`;
}

function toFormState(trip: TripDetail): FormState {
  return {
    title: trip.title,
    startDate: trip.startDate,
    endDate: trip.endDate,
    legs: [],
    notifyMinutes: String(trip.defaultNotifyMinutes),
  };
}

/**
 * 저장 요청. 구간의 도시는 <b>고른 방식 그대로</b> 보낸다.
 *
 * <ul>
 *   <li>검색으로 골랐으면 `cityGooglePlaceId` — 서버가 담고 도시로 승격한다</li>
 *   <li>이미 저장된 도시면 `cityPlaceId`</li>
 *   <li>직접 입력이면 저장 직전에 도시 장소로 만들어 `cityPlaceId`를 얻는다 —
 *       도시가 없으면 그 여행의 어느 날짜도 타임존을 갖지 못한다</li>
 * </ul>
 *
 * <p>수정에서 구간을 하나도 못 채웠으면 `legs`를 <b>보내지 않는다.</b> 그러면 서버가 날짜별
 * 기준 도시를 건드리지 않고 제목·기간·알림만 바꾼다.
 */
async function toRequest(
  form: FormState,
  editing: boolean,
  confirmArchive: boolean,
): Promise<TripWriteRequest> {
  const legs = await Promise.all(form.legs.map(toLegRequest));
  return {
    title: form.title.trim(),
    startDate: form.startDate,
    endDate: form.endDate,
    ...(editing && legs.length === 0 ? {} : { legs }),
    defaultNotifyMinutes: Number(form.notifyMinutes),
    ...(confirmArchive ? { confirmArchive: true } : {}),
  };
}

async function toLegRequest(leg: LegDraft): Promise<TripLegRequest> {
  if (leg.cityGooglePlaceId) {
    return { cityGooglePlaceId: leg.cityGooglePlaceId, days: leg.days };
  }
  if (leg.cityPlaceId !== null) {
    return { cityPlaceId: leg.cityPlaceId, days: leg.days };
  }
  const city = await createManualPlace({
    name: leg.cityName,
    kind: "CITY",
    timezone: leg.timezone,
    currency: leg.currency,
    lat: leg.lat,
    lng: leg.lng,
  });
  return { cityPlaceId: city.id, days: leg.days };
}
