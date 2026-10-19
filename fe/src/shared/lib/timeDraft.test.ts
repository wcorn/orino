import { describe, expect, it } from "vitest";

import { completeTimeDraft, parseTimeDraft } from "./timeDraft";

describe("parseTimeDraft", () => {
  it.each([
    ["1430", "14:30", "14:30"],
    ["14:30", "14:30", "14:30"],
    ["0905", "09:05", "09:05"],
    ["2359", "23:59", "23:59"],
    ["0000", "00:00", "00:00"],
  ])("%s → %s", (raw, display, time) => {
    expect(parseTimeDraft(raw)).toEqual({ display, time });
  });

  it("콜론 앞이 한 자리면 그게 시다 — 9:30은 09:30", () => {
    expect(parseTimeDraft("9:30")).toEqual({ display: "09:30", time: "09:30" });
  });

  it("3 이상으로 시작하면 한 자리 시로 본다 — 30시는 없다", () => {
    expect(parseTimeDraft("930")).toEqual({ display: "09:30", time: "09:30" });
  });

  it("다 치기 전에는 시각이 없다", () => {
    expect(parseTimeDraft("14")).toEqual({ display: "14", time: null });
    expect(parseTimeDraft("143")).toEqual({ display: "14:3", time: null });
  });

  it("범위 밖은 시각이 아니다 — 24시·60분은 없다", () => {
    expect(parseTimeDraft("2400").time).toBeNull();
    expect(parseTimeDraft("1260").time).toBeNull();
  });

  it("숫자가 아닌 글자는 버리고 네 자리에서 자른다", () => {
    expect(parseTimeDraft("오후 2시").display).toBe("2");
    expect(parseTimeDraft("143059").display).toBe("14:30");
  });

  it("지우다 콜론만 남으면 콜론도 지운다", () => {
    expect(parseTimeDraft("14:").display).toBe("14");
  });
});

describe("completeTimeDraft", () => {
  it("시만 쳤으면 정각이다", () => {
    expect(completeTimeDraft("9")).toBe("09:00");
    expect(completeTimeDraft("14")).toBe("14:00");
  });

  it("다 친 값은 그대로다", () => {
    expect(completeTimeDraft("14:30")).toBe("14:30");
  });

  it("마무리할 수 없는 값은 null — 호출자가 원래 값으로 되돌린다", () => {
    expect(completeTimeDraft("14:3")).toBeNull();
    expect(completeTimeDraft("25")).toBeNull();
  });
});
