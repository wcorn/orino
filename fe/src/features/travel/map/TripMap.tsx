import { useEffect, useRef } from "react";

import { mapId, useGoogleMaps } from "./googleMaps";

/** 핀 하나. leaflet divIcon으로 그리던 것과 같은 모양이라 화면은 그대로다. */
function pinElement(order: number, selected: boolean): HTMLElement {
  const size = selected ? 32 : 26;
  const el = document.createElement("div");
  // cssText로 한 번에 넣지 않는다 — 값 하나가 파서에 걸리면 <b>선언 전체가 버려진다</b>
  // (jsdom은 var()가 섞인 cssText를 통째로 무시한다).
  Object.assign(el.style, {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    width: `${size}px`,
    height: `${size}px`,
    borderRadius: "9999px",
    background: "var(--primary)",
    color: "var(--primary-foreground)",
    fontSize: "12px",
    fontWeight: "600",
    border: "2px solid var(--card)",
    boxShadow: "0 1px 3px rgba(0,0,0,.4)",
  });
  el.textContent = String(order);
  return el;
}

/** 지도 위의 점 하나. 하루 동선에서는 일정이고, 여행 전체에서는 도시다. */
export interface MapPoint {
  /** 선택·클릭이 가리키는 값 — 일정 id이거나 도시 placeId다. */
  key: number;
  /** 핀에 찍히는 번호. */
  order: number;
  lat: number;
  lng: number;
  title: string;
}

interface LatLng {
  lat: number;
  lng: number;
}

interface TripMapProps {
  points: MapPoint[];
  /**
   * 연결선 경로. 생략하면 점 순서 그대로 잇는다.
   *
   * <p>따로 받는 이유는 <b>점보다 획이 많을 수 있어서</b>다 — 도쿄 → 닛코 → 도쿄는 점이
   * 둘이지만 선은 갔다 오는 두 획이다.
   */
  path?: LatLng[];
  selectedKey: number | null;
  onSelect: (key: number) => void;
  /** 스크린리더용 이름. 무엇을 그린 지도인지는 모드마다 다르다. */
  label: string;
}

/**
 * 여행 지도(§S-05) — 하루 동선과 여행 전체가 <b>같은 지도</b>를 쓴다.
 *
 * <p>다른 것은 무엇을 점으로 찍느냐뿐이고, SDK 수명주기·정리·불러오기 실패 처리는 같다.
 * 모드마다 지도를 따로 만들면 그 까다로운 부분이 두 벌이 된다.
 *
 * <p>연결선은 <b>직선</b>이다 — 실제 경로가 아니라 순서를 보여주는 선이다. 실제 길찾기는
 * 구글 지도 딥링크가 맡으므로 여기서 경로를 그릴 이유가 없다.
 *
 * <p>구글 지도를 쓴다. 이 화면은 이동시간(Routes 콘텐츠)을 지도 옆에 얹으므로 비구글
 * 지도를 쓸 수 없다(#1102).
 */
export function TripMap({
  points,
  path,
  selectedKey,
  onSelect,
  label,
}: TripMapProps) {
  const status = useGoogleMaps();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<google.maps.Map | null>(null);
  const markersRef = useRef<google.maps.marker.AdvancedMarkerElement[]>([]);
  const lineRef = useRef<google.maps.Polyline | null>(null);
  // 콜백이 바뀔 때마다 마커를 다시 만들지 않도록 최신 값만 들고 있는다.
  const selectRef = useRef(onSelect);
  selectRef.current = onSelect;

  useEffect(() => {
    if (status !== "ready" || !containerRef.current || points.length === 0) {
      return;
    }
    const maps = window.google.maps;
    const line = path ?? points.map(({ lat, lng }) => ({ lat, lng }));

    mapRef.current ??= new maps.Map(containerRef.current, {
      mapId: mapId(),
      // 컨트롤을 다 끄고 지도만 남긴다 — 폰 화면에서 버튼이 핀을 가린다.
      disableDefaultUI: true,
      gestureHandling: "greedy",
      zoom: 14,
      center: { lat: points[0].lat, lng: points[0].lng },
    });
    const map = mapRef.current;

    markersRef.current.forEach((m) => (m.map = null));
    markersRef.current = points.map((point) => {
      const marker = new maps.marker.AdvancedMarkerElement({
        map,
        position: { lat: point.lat, lng: point.lng },
        content: pinElement(point.order, point.key === selectedKey),
        title: `${point.order}. ${point.title}`,
      });
      marker.addListener("click", () => selectRef.current(point.key));
      return marker;
    });

    lineRef.current?.setMap(null);
    lineRef.current =
      line.length > 1
        ? new maps.Polyline({
            map,
            path: line,
            strokeColor: "#8b00ff",
            strokeWeight: 3,
          })
        : null;

    // 핀이 전부 들어오게 범위를 맞춘다.
    if (points.length === 1) {
      map.setCenter({ lat: points[0].lat, lng: points[0].lng });
      map.setZoom(15);
    } else {
      const bounds = new maps.LatLngBounds();
      points.forEach((point) =>
        bounds.extend({ lat: point.lat, lng: point.lng }),
      );
      map.fitBounds(bounds, 24);
    }
  }, [status, points, path, selectedKey]);

  useEffect(() => {
    return () => {
      markersRef.current.forEach((m) => (m.map = null));
      markersRef.current = [];
      lineRef.current?.setMap(null);
      lineRef.current = null;
      mapRef.current = null;
    };
  }, []);

  if (status === "unavailable") {
    return (
      <div className="bg-muted text-muted-foreground grid aspect-[8/5] w-full place-items-center rounded-lg border text-sm">
        지도를 불러오지 못했어요.
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      role="application"
      aria-label={label}
      className="bg-muted aspect-[8/5] w-full overflow-hidden rounded-lg border"
    />
  );
}
