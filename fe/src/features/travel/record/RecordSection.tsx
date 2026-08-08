import { useEffect, useState } from "react";

import { Textarea } from "@/components/ui/textarea";
import type { ActivityLog } from "@/features/travel/api/activities";
import { StarRating } from "@/features/travel/record/StarRating";
import { useAutoSaveLog } from "@/features/travel/record/useAutoSaveLog";

/** 서버 컬럼 길이와 같다. 넘치면 저장이 400으로 튕긴다. */
const RECORD_MEMO_MAX = 2000;

interface RecordSectionProps {
  activityId: number;
  tripId: number;
  log: ActivityLog | null;
  /** 오프라인이면 저장할 수 없다(§S-07). */
  online: boolean;
}

/**
 * S-07 <b>기록 영역</b> — 평점·메모.
 *
 * <p><b>여행이 시작된 뒤에만 이 영역이 존재한다.</b> 호출부가 그 판단을 하고, 여기서는
 * 항상 그려진다 — 아직 겪지 않은 일에 평점을 매기는 화면은 만들지 않는다.
 *
 * <p>저장은 자동이다. 현지에서 "저장" 버튼을 찾게 하지 않는다. 계획 영역과 요청이
 * 분리돼 있어 계획을 저장하지 않아도 기록만 남는다.
 */
export function RecordSection({
  activityId,
  tripId,
  log,
  online,
}: RecordSectionProps) {
  const [rating, setRating] = useState<number | null>(log?.rating ?? null);
  const [memo, setMemo] = useState(log?.memo ?? "");
  const { status, schedule, flush } = useAutoSaveLog(activityId, tripId);

  // 다른 일정으로 넘어가면 그 일정의 기록을 보여준다.
  useEffect(() => {
    setRating(log?.rating ?? null);
    setMemo(log?.memo ?? "");
  }, [activityId, log?.rating, log?.memo]);

  const change = (nextRating: number | null, nextMemo: string) => {
    setRating(nextRating);
    setMemo(nextMemo);
    schedule({ rating: nextRating, memo: nextMemo.trim() || null });
  };

  return (
    <section className="flex flex-col gap-3 border-t pt-5">
      <div className="flex items-center gap-2">
        <h2 className="text-caption text-muted-foreground flex-1 font-semibold">
          기록
        </h2>
        {/* 자동 저장이라 저장 여부가 눈에 보여야 한다. */}
        <span className="text-muted-foreground text-xs" aria-live="polite">
          {!online
            ? "오프라인"
            : status === "saving"
              ? "저장 중…"
              : status === "saved"
                ? "저장됨"
                : status === "error"
                  ? "저장 실패"
                  : ""}
        </span>
      </div>

      <StarRating
        value={rating}
        onChange={(next) => change(next, memo)}
        disabled={!online}
      />

      <Textarea
        aria-label="기록 메모"
        rows={4}
        value={memo}
        placeholder="어땠나요?"
        maxLength={RECORD_MEMO_MAX}
        disabled={!online}
        onChange={(e) => change(rating, e.target.value)}
        // 포커스를 잃으면 디바운스를 기다리지 않는다 — 다 쓴 뒤 화면을 닫는 게 흔하다.
        onBlur={flush}
      />
    </section>
  );
}
