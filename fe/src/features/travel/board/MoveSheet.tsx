import { ExternalLink, Navigation, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import type {
  Activity,
  Move,
  TravelMode,
} from "@/features/travel/api/activities";
import { directionsUrl } from "@/features/travel/lib/mapsLink";
import { TRAVEL_MODES } from "@/features/travel/lib/travelMode";

interface MoveSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 편집할 구간. `mode`가 null이면 아직 아무것도 적지 않은 자리다. */
  move: Move | null;
  /** 딥링크 좌표와 제목을 얻으려면 양 끝 일정이 필요하다. */
  activities: Activity[];
  /** 숙소로 가는 이동이면 그 숙소 이름 — 시트 제목에 쓴다. */
  stayName?: string | null;
  onSave: (input: MoveDraft) => Promise<void>;
  onDelete: () => Promise<void>;
}

export interface MoveDraft {
  mode: TravelMode;
  name: string | null;
  durationMinutes: number | null;
  url: string | null;
  memo: string | null;
}

/**
 * 이동 편집 시트(§S-04). <b>수단을 고르고 시간을 직접 넣는다</b>(#1208).
 *
 * <p>예전에는 앱이 계산한 도보/자동차 중 하나를 고르면 그 값을 사 오는 시트였다. 지금은
 * 사용자가 적는 자리다 — 계산이 못 하던 비행기·신칸센이 여기서 들어온다.
 *
 * <p><b>시간을 확인하러 나가는 통로를 남긴다.</b> 구글 지도 길찾기 버튼이 그것이다. 앱이
 * 노선·환승·실시간 지연을 다시 그릴 이유는 없고, 사용자는 거기서 본 값을 여기 적는다.
 *
 * <p>이름·링크·메모는 <b>선택</b>이다. 수단만 고르고 닫아도 저장된다 — 시간을 나중에
 * 확인하는 것이 실제 순서라, 다 채워야 저장되면 아무것도 저장되지 않는다.
 */
export function MoveSheet({
  open,
  onOpenChange,
  move,
  activities,
  stayName,
  onSave,
  onDelete,
}: MoveSheetProps) {
  const [mode, setMode] = useState<TravelMode | null>(null);
  const [name, setName] = useState("");
  const [minutes, setMinutes] = useState("");
  const [url, setUrl] = useState("");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);

  // 다른 구간을 탭하면 이전 구간의 입력이 남아 있으면 안 된다.
  useEffect(() => {
    if (!move) return;
    setMode(move.mode);
    setName(move.name ?? "");
    setMinutes(
      move.durationMinutes === null ? "" : String(move.durationMinutes),
    );
    setUrl(move.url ?? "");
    setMemo(move.memo ?? "");
  }, [move]);

  if (!move) return null;

  const from = activities.find((a) => a.id === move.fromActivityId);
  const to =
    move.toActivityId === null
      ? null
      : activities.find((a) => a.id === move.toActivityId);
  const destination = to?.title ?? stayName ?? "숙소";
  // 숙소는 보드 응답에 좌표가 없다 — 그 길찾기는 숙소 배지가 이미 열어 준다.
  const mapsUrl =
    from?.place && to?.place ? directionsUrl(from.place, to.place) : null;

  const parsedMinutes = minutes.trim() === "" ? null : Number(minutes);
  const minutesInvalid =
    parsedMinutes !== null &&
    (!Number.isInteger(parsedMinutes) ||
      parsedMinutes < 1 ||
      parsedMinutes > 10080);

  const save = async () => {
    if (mode === null || minutesInvalid || saving) return;
    setSaving(true);
    try {
      await onSave({
        mode,
        name: name.trim() || null,
        durationMinutes: parsedMinutes,
        url: url.trim() || null,
        memo: memo.trim() || null,
      });
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    setSaving(true);
    try {
      await onDelete();
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="이동"
      description={from ? `${from.title} → ${destination}` : undefined}
    >
      <div className="flex flex-col gap-4">
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1.5 text-sm font-medium">이동수단</legend>
          <div className="grid grid-cols-3 gap-1.5">
            {TRAVEL_MODES.map(({ mode: value, label, Icon }) => (
              <button
                key={value}
                type="button"
                onClick={() => setMode(value)}
                aria-pressed={mode === value}
                className={`flex flex-col items-center gap-1 rounded-lg border px-2 py-2.5 text-xs ${
                  mode === value
                    ? "border-primary bg-accent"
                    : "border-border hover:bg-accent"
                }`}
              >
                <Icon className="text-muted-foreground size-4" />
                {label}
              </button>
            ))}
          </div>
        </fieldset>

        {/* 분류만으로는 현지에서 무엇을 찾아야 할지 모른다 — 실제로 타는 것을 적는다. */}
        <FormField label="이동수단 이름" htmlFor="move-name">
          <Input
            id="move-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="나리타 익스프레스 3호"
            maxLength={100}
          />
        </FormField>

        <FormField
          label="소요 시간(분)"
          htmlFor="move-minutes"
          error={
            minutesInvalid ? "1분에서 10080분 사이로 적어 주세요" : undefined
          }
        >
          <Input
            id="move-minutes"
            type="number"
            inputMode="numeric"
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
            placeholder="아직 몰라도 비워 둘 수 있어요"
          />
        </FormField>

        <FormField label="예매 · 확인 링크" htmlFor="move-url">
          <Input
            id="move-url"
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://"
            maxLength={500}
          />
        </FormField>

        <FormField label="메모" htmlFor="move-memo">
          <Input
            id="move-memo"
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            placeholder="5호차 12A · 예약번호"
            maxLength={500}
          />
        </FormField>

        {/* 적어 둔 링크는 여기서 바로 연다 — 현지에서 티켓을 꺼내는 자리가 이 시트다. */}
        {move.url && (
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={() => window.open(move.url!, "_blank", "noopener")}
          >
            <ExternalLink className="size-3.5" />
            저장한 링크 열기
          </Button>
        )}

        {/* 시간을 직접 확인하러 나가는 통로. 딥링크는 언제나 대중교통이다(§4.5). */}
        {mapsUrl && (
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={() => window.open(mapsUrl, "_blank", "noopener")}
          >
            <Navigation className="size-3.5" />
            구글 지도에서 시간 확인 (대중교통)
          </Button>
        )}

        <div className="flex gap-2">
          {move.mode !== null && (
            <Button
              variant="outline"
              size="sm"
              disabled={saving}
              onClick={() => void remove()}
              aria-label="이동 지우기"
            >
              <Trash2 className="size-3.5" />
            </Button>
          )}
          <Button
            className="flex-1"
            size="sm"
            disabled={mode === null || minutesInvalid || saving}
            onClick={() => void save()}
          >
            저장
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}
