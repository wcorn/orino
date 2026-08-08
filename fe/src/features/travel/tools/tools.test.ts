import { describe, expect, it } from "vitest";

import { convert, formatAmount, parseAmount } from "./money";
import { destinationLanguage, translateUrl } from "./translateLink";
import { needsUmbrella } from "./weatherIcon";

describe("환율 입력", () => {
  it("콤마가 섞여도 읽는다 — 계산 결과를 그대로 다시 넣게 되기 때문이다", () => {
    expect(parseAmount("10,000")).toBe(10000);
    expect(parseAmount(" 1,234.5 ")).toBe(1234.5);
  });

  it("빈 칸과 잘못된 입력은 둘 다 null", () => {
    expect(parseAmount("")).toBeNull();
    expect(parseAmount("abc")).toBeNull();
    // 음수 환전은 없다.
    expect(parseAmount("-100")).toBeNull();
  });

  it("천단위 콤마를 붙이고 소수는 두 자리까지만", () => {
    expect(formatAmount(1234567)).toBe("1,234,567");
    expect(formatAmount(8.760402)).toBe("8.76");
  });

  it("양방향 환산이 서로를 되돌린다", () => {
    const rate = 8.9427;
    const krw = convert(10000, rate);

    expect(krw).toBeCloseTo(89427, 0);
    // 반대로 돌리면 원래 금액 근처로 온다(표시용 반올림 오차 범위).
    expect(convert(krw, 1 / rate)).toBeCloseTo(10000, 1);
  });
});

describe("강수 강조", () => {
  it("60% 이상이면 우산 — 이 카드를 보는 이유가 이것이다", () => {
    expect(needsUmbrella(60)).toBe(true);
    expect(needsUmbrella(85)).toBe(true);
  });

  it("60% 미만이면 그대로", () => {
    expect(needsUmbrella(59)).toBe(false);
    expect(needsUmbrella(0)).toBe(false);
  });

  it("확률을 모르면 강조하지 않는다 — 없는 걸 경고로 만들지 않는다", () => {
    expect(needsUmbrella(null)).toBe(false);
  });
});

describe("번역 딥링크", () => {
  it("여행 타임존에서 목적지 언어를 유추한다", () => {
    expect(destinationLanguage("Asia/Tokyo")).toBe("ja");
    expect(destinationLanguage("Asia/Bangkok")).toBe("th");
    expect(destinationLanguage("Europe/Paris")).toBe("fr");
  });

  it("모르는 곳은 영어로 연다 — 구글 번역에서 바꿀 수 있어 막다른 길이 아니다", () => {
    expect(destinationLanguage("America/Argentina/Ushuaia")).toBe("en");
  });

  it("한국어에서 출발한다", () => {
    const url = new URL(translateUrl("Asia/Tokyo"));

    expect(url.searchParams.get("sl")).toBe("ko");
    expect(url.searchParams.get("tl")).toBe("ja");
  });
});
