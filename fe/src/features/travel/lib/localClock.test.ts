import { describe, expect, it } from "vitest";

import { localTime, sameOffset } from "./localClock";

/**
 * 테스트가 도는 기기의 타임존은 고정할 수 없다. 그래서 "특정 시각"이 아니라
 * <b>오프셋 관계</b>를 확인한다 — 그게 이 화면이 실제로 판단하는 것이다.
 */
describe("현지 시각 줄", () => {
  const at = new Date("2026-10-24T00:00:00Z");

  it("기기 타임존과 같으면 숨긴다 — 시계가 똑같아 줄만 차지한다", () => {
    const deviceZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

    expect(sameOffset(deviceZone, at)).toBe(true);
  });

  it("오프셋이 다르면 보여준다", () => {
    // UTC+9와 UTC+7은 어느 기기에서 돌려도 서로 다르다.
    expect(sameOffset("Asia/Tokyo", at) && sameOffset("Asia/Bangkok", at)).toBe(
      false,
    );
  });

  it("도쿄와 서울은 같은 오프셋이라 한쪽만 보고 판단하면 안 된다", () => {
    expect(sameOffset("Asia/Tokyo", at)).toBe(sameOffset("Asia/Seoul", at));
  });

  it("현지 시각을 24시간 표기로 준다", () => {
    // 2026-10-24T00:00Z = 도쿄 09:00.
    expect(localTime("Asia/Tokyo", at)).toBe("09:00");
    // 방콕은 UTC+7이라 07:00.
    expect(localTime("Asia/Bangkok", at)).toBe("07:00");
  });

  it("자정을 24시가 아니라 00시로 쓴다", () => {
    expect(localTime("UTC", new Date("2026-10-24T00:00:00Z"))).toBe("00:00");
  });
});
