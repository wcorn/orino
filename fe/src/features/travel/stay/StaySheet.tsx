import {
  CalendarPlus,
  Hotel,
  Link as LinkIcon,
  MapPin,
  Navigation,
  StickyNote,
} from "lucide-react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import type { PlaceDetail } from "@/features/travel/api/places";
import type { Stay } from "@/features/travel/api/stays";
import { placeDirectionsUrl } from "@/features/travel/lib/mapsLink";
import { formatShortDate } from "@/features/travel/lib/tripStatus";

interface StaySheetProps {
  /** 열려 있는 숙소. null이면 닫혀 있다. */
  stay: Stay | null;
  onOpenChange: (open: boolean) => void;
  onEdit: (stay: Stay) => void;
  onDelete: (stay: Stay) => void;
  /** `일정으로 추가` — **누를 때만** 일정을 만든다. */
  onAddActivity: (stay: Stay) => void;
  offline: boolean;
  /** 일정 생성 중. 두 번 눌러 일정이 두 벌 생기면 안 된다. */
  addingActivity: boolean;
  /** 붙어 있는 장소의 상세. 주소·좌표는 여기서만 온다 — 숙소 자체는 id만 들고 있다. */
  place: PlaceDetail | null;
}

/**
 * 숙소 상세 시트(§9.6). 보드의 숙소 배지 탭으로 연다.
 *
 * <p>체크인·체크아웃 일정을 <b>자동으로 만들지 않는다.</b> 자동 생성하면 지워도 다음 조회에
 * 되살아나는 일정이 된다 — 사용자가 지운 것을 앱이 되돌리면 안 된다. `일정으로 추가`를
 * 눌렀을 때만 만들고, 그 뒤로는 숙소와 무관한 보통 일정이다(숙소를 지워도 남는다).
 */
export function StaySheet({
  stay,
  onOpenChange,
  onEdit,
  onDelete,
  onAddActivity,
  offline,
  addingActivity,
  place,
}: StaySheetProps) {
  const mapsUrl = place ? placeDirectionsUrl(place) : null;
  return (
    <BottomSheet
      open={stay !== null}
      onOpenChange={onOpenChange}
      title="숙소"
      description={stay ? `${stay.nights}박` : undefined}
    >
      {stay && (
        <div className="flex flex-col gap-3">
          <p className="flex items-center gap-2 text-[15px] font-semibold">
            <Hotel className="size-[17px] shrink-0" />
            {stay.name}
          </p>

          {/* 주소·도시는 붙어 있는 장소에서 온다. 장소 없는 숙소는 이 줄이 없다. */}
          {place?.address && (
            <p className="text-muted-foreground flex items-start gap-1.5 text-xs">
              <MapPin className="mt-px size-3 shrink-0" />
              {place.address}
            </p>
          )}

          <div className="flex gap-2">
            <TimeBox
              label="체크인"
              date={stay.checkInDate}
              time={stay.checkInTime}
            />
            <TimeBox
              label="체크아웃"
              date={stay.checkOutDate}
              time={stay.checkOutTime}
            />
          </div>

          {stay.memo && (
            <p className="bg-muted flex items-start gap-2 rounded-lg px-3 py-2 text-[13px]">
              <StickyNote className="mt-px size-3.5 shrink-0" />
              {stay.memo}
            </p>
          )}

          {stay.bookingUrl && (
            <a
              href={stay.bookingUrl}
              target="_blank"
              rel="noreferrer noopener"
              className="text-primary flex items-center gap-1.5 text-[13px] underline underline-offset-2"
            >
              <LinkIcon className="size-3.5 shrink-0" />
              예약 확인
            </a>
          )}

          {/* 좌표가 있어야 길찾기가 성립한다 — 이름만으로는 엉뚱한 곳을 연다. */}
          {mapsUrl && (
            <Button
              variant="outline"
              size="sm"
              className="w-full"
              onClick={() => window.open(mapsUrl, "_blank", "noopener")}
            >
              <Navigation className="size-3.5" />
              구글 지도에서 길찾기 (대중교통)
            </Button>
          )}

          {!offline && (
            <Button
              variant="outline"
              size="sm"
              className="w-full"
              disabled={addingActivity}
              onClick={() => onAddActivity(stay)}
            >
              <CalendarPlus className="size-3.5" />
              일정으로 추가
            </Button>
          )}

          <div className="flex gap-2 pt-1">
            <Button
              variant="outline"
              size="sm"
              className="flex-1"
              onClick={() => onOpenChange(false)}
            >
              닫기
            </Button>
            {!offline && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  className="flex-1"
                  onClick={() => onEdit(stay)}
                >
                  수정
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="text-destructive flex-1"
                  onClick={() => onDelete(stay)}
                >
                  삭제
                </Button>
              </>
            )}
          </div>
        </div>
      )}
    </BottomSheet>
  );
}

/** 시각이 없으면 날짜만 — 없는 시각을 `00:00`으로 채우면 그 시각에 맞춰 움직이게 된다. */
function TimeBox({
  label,
  date,
  time,
}: {
  label: string;
  date: string;
  time: string | null;
}) {
  return (
    <div className="flex-1 rounded-lg border px-3 py-2.5">
      <p className="text-muted-foreground text-[11px]">{label}</p>
      <p className="text-sm tabular-nums">
        {formatShortDate(date)}
        {time && ` ${time}`}
      </p>
    </div>
  );
}
