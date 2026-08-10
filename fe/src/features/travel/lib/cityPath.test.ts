import { describe, expect, it } from "vitest";

import type { TripCitySummary } from "@/features/travel/api/travel";

import {
  formatCities,
  formatCityCount,
  formatCityPath,
  formatTodayCity,
} from "./cityPath";

function cities(
  names: string[],
  overrides: Partial<TripCitySummary> = {},
): TripCitySummary {
  return {
    names,
    count: new Set(names).size,
    today: null,
    movedFrom: null,
    todayDayIndex: null,
    todayTimezone: null,
    todayCurrency: null,
    ...overrides,
  };
}

describe("formatCityPath", () => {
  it("도시가 하나면 이름만", () => {
    expect(formatCityPath(["오사카"])).toBe("오사카");
  });

  it("4개까지는 다 보여준다", () => {
    expect(formatCityPath(["오사카", "교토", "나라", "고베"])).toBe(
      "오사카 → 교토 → 나라 → 고베",
    );
  });

  it("4개를 넘으면 처음 둘과 마지막만 — 출발지와 도착지가 여행의 모양을 말한다", () => {
    expect(
      formatCityPath(["오사카", "교토", "나라", "고베", "나고야", "도쿄"]),
    ).toBe("오사카 → 교토 → … → 도쿄");
  });

  it("연속 중복은 접는다", () => {
    expect(formatCityPath(["도쿄", "도쿄", "닛코"])).toBe("도쿄 → 닛코");
  });

  it("떨어져 있는 중복은 남긴다 — 돌아온 것이라 지우면 닛코에서 끝난 것처럼 보인다", () => {
    expect(formatCityPath(["도쿄", "닛코", "도쿄"])).toBe("도쿄 → 닛코 → 도쿄");
  });

  it("도시가 없으면 빈 문자열", () => {
    expect(formatCityPath([])).toBe("");
  });
});

describe("formatCityCount", () => {
  it("하나면 셀 것이 없다", () => {
    expect(formatCityCount(1)).toBe("");
  });

  it("둘 이상이면 개수를 말한다", () => {
    expect(formatCityCount(6)).toBe("6개 도시");
  });
});

describe("formatCities", () => {
  it("나열과 개수를 한 줄로", () => {
    expect(
      formatCities(
        cities(["오사카", "교토", "나라", "고베", "나고야", "도쿄"]),
      ),
    ).toBe("오사카 → 교토 → … → 도쿄 (6개 도시)");
  });

  it("단일 도시 여행은 이름만 — v2.0과 같은 모양이다", () => {
    expect(formatCities(cities(["도쿄"]))).toBe("도쿄");
  });

  it("같은 도시를 다시 방문해도 개수는 하나가 아니라 서로 다른 도시 수다", () => {
    expect(formatCities(cities(["도쿄", "닛코", "도쿄"]))).toBe(
      "도쿄 → 닛코 → 도쿄 (2개 도시)",
    );
  });

  it("값이 없으면 빈 문자열 — 더미를 넣지 않는다", () => {
    expect(formatCities(undefined)).toBe("");
    expect(formatCities(cities([]))).toBe("");
  });
});

describe("formatTodayCity", () => {
  it("오늘의 도시를 말한다", () => {
    expect(formatTodayCity(cities(["교토"], { today: "교토" }))).toBe("교토");
  });

  it("옮기는 날이면 어디서 어디로인지 말한다", () => {
    expect(
      formatTodayCity(
        cities(["오사카", "교토"], { today: "교토", movedFrom: "오사카" }),
      ),
    ).toBe("오사카 → 교토");
  });

  it("진행 중이 아니면 빈 문자열", () => {
    expect(formatTodayCity(cities(["교토"]))).toBe("");
    expect(formatTodayCity(undefined)).toBe("");
  });
});
