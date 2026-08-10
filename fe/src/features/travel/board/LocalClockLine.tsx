import { useEffect, useState } from "react";

import { localTime, sameOffset } from "@/features/travel/lib/localClock";

interface LocalClockLineProps {
  /** 보고 있는 날짜의 기준 도시. 도시를 옮겨 다니면 탭을 넘길 때마다 이 줄이 바뀐다. */
  cityName: string;
  timezone: string;
  currency: string;
  /** 완료된 여행은 시계가 아니라 기록 모드임을 알린다. */
  recordMode: boolean;
}

/**
 * 제목 아래 부제(§S-04) — `현지 09:42 · 교토 · Asia/Tokyo · JPY`.
 *
 * <p>기기와 <b>오프셋이 같으면 시각만 감춘다</b>. 서울에서 도쿄 여행을 보면 시계가 똑같아
 * 알려 줄 것이 없지만, 도시와 타임존은 v2.1에서 <b>날짜마다 달라지는 값</b>이라 계속 필요하다
 * (v2.0에서는 줄 전체를 감췄다 — 그때는 여행 하나에 타임존도 하나였다).
 */
export function LocalClockLine({
  cityName,
  timezone,
  currency,
  recordMode,
}: LocalClockLineProps) {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    // 분 단위 표시라 30초면 충분하다. 더 자주 돌 이유가 없다.
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, []);

  if (recordMode) {
    return (
      <p className="text-caption text-muted-foreground">
        기록 모드 · {cityName} · {timezone}
      </p>
    );
  }
  const parts = sameOffset(timezone, now)
    ? [cityName, timezone, currency]
    : [`현지 ${localTime(timezone, now)}`, cityName, timezone, currency];
  return (
    <p className="text-caption text-muted-foreground">{parts.join(" · ")}</p>
  );
}
