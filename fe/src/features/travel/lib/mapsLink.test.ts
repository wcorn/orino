import { describe, expect, it } from "vitest";

import { directionsUrl, placeDirectionsUrl } from "./mapsLink";

const SENSOJI = { lat: 35.7147651, lng: 139.7966553 };
const SKYTREE = { lat: 35.7100627, lng: 139.8107004 };

describe("구글 지도 딥링크", () => {
  it("이동수단은 항상 대중교통이다 — 앱 내 표시가 도보/자동차여도 상관없다", () => {
    const url = directionsUrl(SENSOJI, SKYTREE);

    // 앱은 노선·환승·요금을 다루지 않는다. 길찾기는 통째로 넘긴다(§4.5).
    expect(url).toContain("travelmode=transit");
  });

  it("좌표로 전달한다 — 같은 이름의 장소가 여럿이면 엉뚱한 곳이 열린다", () => {
    const url = new URL(directionsUrl(SENSOJI, SKYTREE)!);

    expect(url.searchParams.get("origin")).toBe("35.7147651,139.7966553");
    expect(url.searchParams.get("destination")).toBe("35.7100627,139.8107004");
    expect(url.searchParams.get("api")).toBe("1");
  });

  it("좌표가 없으면 링크를 만들지 않는다 — 직접 입력한 장소는 좌표가 없다", () => {
    expect(directionsUrl(SENSOJI, { lat: null, lng: null })).toBeNull();
    expect(directionsUrl({ lat: null, lng: null }, SKYTREE)).toBeNull();
  });

  it("목적지만 여는 링크에는 origin이 없다 — 현재 위치에서 출발한다", () => {
    const url = new URL(placeDirectionsUrl(SKYTREE)!);

    expect(url.searchParams.has("origin")).toBe(false);
    expect(url.searchParams.get("destination")).toBe("35.7100627,139.8107004");
    expect(url.searchParams.get("travelmode")).toBe("transit");
  });

  it("좌표 없는 장소는 목적지 링크도 만들지 않는다", () => {
    expect(placeDirectionsUrl({ lat: null, lng: null })).toBeNull();
  });
});
