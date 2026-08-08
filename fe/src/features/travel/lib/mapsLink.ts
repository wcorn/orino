interface Point {
  lat: number | null;
  lng: number | null;
}

/**
 * 구글 지도 길찾기 딥링크(§4.5).
 *
 * <p>**이동수단은 항상 대중교통이다.** 앱 내 표시가 도보든 자동차든 상관없다 — 앱은 노선·환승·
 * 요금·실시간 지연을 다루지 않고, 그건 구글 지도가 훨씬 잘한다. 앱이 계산하는 도보/자동차는
 * "대충 얼마나 걸리나"를 계획 단계에서 알기 위한 값이고, 실제로 길을 찾을 땐 넘긴다.
 *
 * <p>좌표로 전달한다 — 장소 이름은 같은 이름이 여럿이라 엉뚱한 곳을 열 수 있다.
 */
export function directionsUrl(from: Point, to: Point): string | null {
  if (
    from.lat === null ||
    from.lng === null ||
    to.lat === null ||
    to.lng === null
  ) {
    return null;
  }
  const params = new URLSearchParams({
    api: "1",
    origin: `${from.lat},${from.lng}`,
    destination: `${to.lat},${to.lng}`,
    travelmode: "transit",
  });
  return `https://www.google.com/maps/dir/?${params.toString()}`;
}

/** 목적지 하나만 여는 링크 — 일정 상세의 장소 블록에서 쓴다. */
export function placeDirectionsUrl(to: Point): string | null {
  if (to.lat === null || to.lng === null) return null;
  const params = new URLSearchParams({
    api: "1",
    destination: `${to.lat},${to.lng}`,
    travelmode: "transit",
  });
  return `https://www.google.com/maps/dir/?${params.toString()}`;
}
