import { useEffect, useState } from "react";

import { localTime, sameOffset } from "@/features/travel/lib/localClock";

interface LocalClockLineProps {
  timezone: string;
  /** 완료된 여행은 시계가 아니라 기록 모드임을 알린다. */
  recordMode: boolean;
}

/**
 * 제목 아래 현지 시각 줄(§S-04).
 *
 * <p>기기와 여행의 <b>오프셋이 같으면 숨긴다</b> — 서울에서 도쿄 여행을 보면 시계가 똑같아
 * 줄만 차지한다. 이 줄이 필요한 건 "지금 현지가 몇 시지"가 실제로 헷갈릴 때뿐이다.
 */
export function LocalClockLine({ timezone, recordMode }: LocalClockLineProps) {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    // 분 단위 표시라 30초면 충분하다. 더 자주 돌 이유가 없다.
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, []);

  if (recordMode) {
    return (
      <p className="text-caption text-muted-foreground">
        기록 모드 · {timezone}
      </p>
    );
  }
  if (sameOffset(timezone, now)) {
    return null;
  }
  return (
    <p className="text-caption text-muted-foreground">
      현지 {localTime(timezone, now)} · {timezone}
    </p>
  );
}
