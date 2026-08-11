import { type FormEvent, useEffect, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type { BaseCity } from "@/features/travel/api/activities";
import type { Stay, StayWriteRequest } from "@/features/travel/api/stays";
import { overlapMessage, overlaps } from "@/features/travel/lib/stayForDay";
import { formatShortDate } from "@/features/travel/lib/tripStatus";
import type { PickedPlace } from "@/features/travel/stay/StayPlacePicker";
import { StayPlacePicker } from "@/features/travel/stay/StayPlacePicker";

interface StayFormSheetProps {
  open: boolean;
  /** 수정 대상. null이면 새로 등록한다. */
  stay: Stay | null;
  tripStartDate: string;
  tripEndDate: string;
  /** 이미 있는 숙소들 — 겹침을 <b>서버에 묻기 전에</b> 답하는 데 쓴다. */
  stays: Stay[];
  tripId: number;
  /** 이 여행에 등장하는 도시들. 숙소가 어느 도시에 있는지 여기서 고른다. */
  cities: BaseCity[];
  /** 기본값 — 보고 있던 날짜의 도시. 숙소를 그 날짜 근처에 잡는 경우가 대부분이다. */
  defaultCity: BaseCity | null;
  onOpenChange: (open: boolean) => void;
  /** 저장. 실패하면 던져야 한다 — 시트가 열린 채 사유를 보여준다. */
  onSubmit: (body: StayWriteRequest) => Promise<void>;
  pending: boolean;
  /** 서버가 돌려준 겹침 안내. 클라이언트가 놓친 경우에만 채워진다. */
  serverError: string | null;
}

interface Draft {
  name: string;
  checkInDate: string;
  checkOutDate: string;
  checkInTime: string;
  checkOutTime: string;
  bookingUrl: string;
  memo: string;
}

const EMPTY: Draft = {
  name: "",
  checkInDate: "",
  checkOutDate: "",
  checkInTime: "",
  checkOutTime: "",
  bookingUrl: "",
  memo: "",
};

/**
 * 숙소 등록·수정 폼(§9.6).
 *
 * <p><b>겹침을 서버에 묻기 전에 답한다.</b> 저장을 눌러야 "안 된다"를 알게 되면 사용자는
 * 이미 입력을 다 마친 뒤다. 같은 규칙(반열린 구간 `[in, out)`)이 FE에도 있는 이유는 중복이
 * 아니라 <b>답하는 시점이 다르기</b> 때문이고, 최종 판단은 여전히 서버가 한다.
 */
export function StayFormSheet({
  open,
  stay,
  tripStartDate,
  tripEndDate,
  stays,
  tripId,
  cities,
  defaultCity,
  onOpenChange,
  onSubmit,
  pending,
  serverError,
}: StayFormSheetProps) {
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  /** 새로 고른 장소. 수정에서 손대지 않으면 null이고, 그때는 붙어 있던 장소를 그대로 둔다. */
  const [picked, setPicked] = useState<PickedPlace | null>(null);
  /**
   * 사용자가 고른 도시. 안 골랐으면 보던 날짜의 도시로 떨어진다 — 상태로 굳히지 않는 이유는
   * 시트를 열 때마다 그 날짜를 따라가야 하기 때문이다.
   */
  const [pickedCity, setPickedCity] = useState<BaseCity | null>(null);
  const city = pickedCity ?? defaultCity ?? cities[0] ?? null;

  // 다른 숙소를 열면 이전 입력이 남아 있으면 안 된다.
  useEffect(() => {
    if (!open) return;
    setError(null);
    setPicked(null);
    setPickedCity(null);
    setDraft(
      stay === null
        ? EMPTY
        : {
            name: stay.name,
            checkInDate: stay.checkInDate,
            checkOutDate: stay.checkOutDate,
            checkInTime: stay.checkInTime ?? "",
            checkOutTime: stay.checkOutTime ?? "",
            bookingUrl: stay.bookingUrl ?? "",
            memo: stay.memo ?? "",
          },
    );
  }, [open, stay]);

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setDraft((prev) => ({ ...prev, [key]: value }));

  const validate = (): string | null => {
    if (!draft.name.trim()) return "숙소 이름을 입력해 주세요.";
    if (!draft.checkInDate || !draft.checkOutDate)
      return "체크인·체크아웃 날짜를 입력해 주세요.";
    if (draft.checkOutDate <= draft.checkInDate)
      return "체크아웃은 체크인 다음 날부터예요.";
    if (draft.checkInDate < tripStartDate)
      return "체크인은 여행 시작일부터예요.";
    if (draft.checkOutDate > tripEndDate)
      // 마지막 밤에 묵으려면 여행이 하루 더 길어야 한다 — 막기만 하면 왜 안 되는지 모른다.
      return "체크아웃일도 여행 기간 안이어야 해요. 마지막 밤에 묵으려면 여행 기간을 하루 늘려주세요.";

    const conflict = stays.find(
      (other) =>
        other.stayId !== stay?.stayId &&
        overlaps(other, {
          checkInDate: draft.checkInDate,
          checkOutDate: draft.checkOutDate,
        }),
    );
    return conflict ? overlapMessage(conflict) : null;
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const message = validate();
    if (message !== null) {
      setError(message);
      return;
    }
    setError(null);
    // 빈 문자열은 "지움"이다 — 선택 항목을 비워 저장하면 실제로 비워져야 한다.
    await onSubmit({
      name: draft.name.trim(),
      // 새로 골랐으면 그것으로 바꾸고, 아니면 붙어 있던 장소를 그대로 실어 보낸다 —
      // 수정은 전체 교체라 빼면 좌표가 사라져 숙소 이동 시간이 계산되지 않는다.
      ...(picked
        ? { googlePlaceId: picked.googlePlaceId, cityPlaceId: city?.placeId }
        : { placeId: stay?.placeId ?? null }),
      checkInDate: draft.checkInDate,
      checkOutDate: draft.checkOutDate,
      checkInTime: draft.checkInTime || null,
      checkOutTime: draft.checkOutTime || null,
      bookingUrl: draft.bookingUrl.trim() || null,
      memo: draft.memo.trim() || null,
    });
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title={stay === null ? "숙소 추가" : "숙소 수정"}
      description={`${formatShortDate(tripStartDate)} – ${formatShortDate(tripEndDate)} 안에서 고를 수 있어요`}
    >
      <form
        onSubmit={(e) => void submit(e)}
        noValidate
        className="flex flex-col gap-3"
      >
        <FormField label="이름" htmlFor="stay-name">
          <Input
            id="stay-name"
            value={draft.name}
            onChange={(e) => set("name", e.target.value)}
            placeholder="도톤보리 호텔"
          />
        </FormField>

        {/* 장소는 선택이다 — 붙이면 이동시간과 길찾기가 살아나고, 없어도 숙소는 저장된다. */}
        <FormField label="장소 (선택)">
          <StayPlacePicker
            tripId={tripId}
            picked={picked}
            onPick={(place) => {
              setPicked(place);
              // 이름을 아직 안 적었으면 고른 장소 이름으로 채운다.
              if (place && !draft.name.trim()) set("name", place.name);
            }}
            city={city}
            cities={cities}
            onCityChange={setPickedCity}
          />
        </FormField>

        <div className="flex gap-2">
          <FormField label="체크인" htmlFor="stay-in" className="flex-1">
            <Input
              id="stay-in"
              type="date"
              value={draft.checkInDate}
              min={tripStartDate}
              max={tripEndDate}
              onChange={(e) => set("checkInDate", e.target.value)}
            />
          </FormField>
          <FormField label="체크아웃" htmlFor="stay-out" className="flex-1">
            <Input
              id="stay-out"
              type="date"
              value={draft.checkOutDate}
              min={tripStartDate}
              max={tripEndDate}
              onChange={(e) => set("checkOutDate", e.target.value)}
            />
          </FormField>
        </div>

        <div className="flex gap-2">
          <FormField
            label="체크인 시각"
            htmlFor="stay-in-time"
            className="flex-1"
          >
            <Input
              id="stay-in-time"
              type="time"
              value={draft.checkInTime}
              onChange={(e) => set("checkInTime", e.target.value)}
            />
          </FormField>
          <FormField
            label="체크아웃 시각"
            htmlFor="stay-out-time"
            className="flex-1"
          >
            <Input
              id="stay-out-time"
              type="time"
              value={draft.checkOutTime}
              onChange={(e) => set("checkOutTime", e.target.value)}
            />
          </FormField>
        </div>

        <FormField label="예약 링크" htmlFor="stay-url">
          <Input
            id="stay-url"
            type="url"
            value={draft.bookingUrl}
            onChange={(e) => set("bookingUrl", e.target.value)}
            placeholder="https://"
          />
        </FormField>

        <FormField label="메모" htmlFor="stay-memo">
          <Textarea
            id="stay-memo"
            rows={2}
            value={draft.memo}
            onChange={(e) => set("memo", e.target.value)}
          />
        </FormField>

        {(error ?? serverError) && (
          <FieldError>{error ?? serverError}</FieldError>
        )}

        <div className="flex gap-2 pt-1">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="flex-1"
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button type="submit" size="sm" className="flex-1" disabled={pending}>
            저장
          </Button>
        </div>
      </form>
    </BottomSheet>
  );
}
