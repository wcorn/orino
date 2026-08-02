import { describe, expect, it } from "vitest";

import { formatCompletedLabel, formatUpcomingLabel } from "./time";

// 기준 현재: 2026-05-18(월) 20:00 로컬
const NOW = new Date(2026, 4, 18, 20, 0, 0);

describe("formatUpcomingLabel", () => {
  it("과거·현재(due)는 '지금'", () => {
    expect(formatUpcomingLabel("2026-05-18T19:50:00", NOW)).toBe("지금");
    expect(formatUpcomingLabel("2026-05-18T20:00:00", NOW)).toBe("지금");
  });

  it("1시간 미만은 'N분 후'", () => {
    expect(formatUpcomingLabel("2026-05-18T20:10:00", NOW)).toBe("10분 후");
  });

  it("같은 날 1시간 이상은 '오늘 HH:MM'", () => {
    expect(formatUpcomingLabel("2026-05-18T22:30:00", NOW)).toBe("오늘 22:30");
  });

  it("내일은 '내일 MM/DD'", () => {
    expect(formatUpcomingLabel("2026-05-19T04:00:00", NOW)).toBe("내일 05/19");
  });

  it("모레 이후는 'MM/DD'", () => {
    expect(formatUpcomingLabel("2026-05-25T04:00:00", NOW)).toBe("05/25");
  });

  it("새벽 2시에 보면 그날 04:00은 '내일' — 학습일이 아직 안 넘어갔다 (#1003)", () => {
    const lateNight = new Date(2026, 4, 19, 2, 0); // 학습일 5/18

    expect(formatUpcomingLabel("2026-05-19T04:00:00", lateNight)).toBe(
      "내일 05/19",
    );
  });
});

describe("formatCompletedLabel", () => {
  it("오늘은 '오늘 HH:MM'", () => {
    expect(formatCompletedLabel("2026-05-18T09:12:00", NOW)).toBe("오늘 09:12");
  });

  it("어제는 '어제 HH:MM'", () => {
    expect(formatCompletedLabel("2026-05-17T22:10:00", NOW)).toBe("어제 22:10");
  });

  it("그 이전은 'MM/DD HH:MM'", () => {
    expect(formatCompletedLabel("2026-05-10T14:05:00", NOW)).toBe(
      "05/10 14:05",
    );
  });

  it("어젯밤 새벽에 한 복습은 낮에 보면 '어제' (#1003)", () => {
    // 5/18 01:30은 학습일 5/17 — 5/18 낮에 보면 어제 몫이다
    expect(formatCompletedLabel("2026-05-18T01:30:00", NOW)).toBe("어제 01:30");
  });
});
