import { useEffect, useRef } from "react";

import { mapId, useGoogleMaps } from "./googleMaps";
import type { MappedActivity } from "./toMapped";

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

interface TripDayMapProps {
  mapped: MappedActivity[];
  selectedId: number | null;
  onSelect: (activityId: number) => void;
}

/**
 * 하루 동선(§S-05).
 *
 * <p>연결선은 <b>직선</b>이다 — 실제 경로가 아니라 순서를 보여주는 선이다. 실제 길찾기는
 * 구글 지도 딥링크가 맡으므로 여기서 경로를 그릴 이유가 없다.
 *
 * <p>구글 지도를 쓴다. 이 화면은 이동시간(Routes 콘텐츠)을 지도 옆에 얹으므로 비구글
 * 지도를 쓸 수 없다(#1102).
 */
export function TripDayMap({ mapped, selectedId, onSelect }: TripDayMapProps) {
  const status = useGoogleMaps();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<google.maps.Map | null>(null);
  const markersRef = useRef<google.maps.marker.AdvancedMarkerElement[]>([]);
  const lineRef = useRef<google.maps.Polyline | null>(null);
  // 콜백이 바뀔 때마다 마커를 다시 만들지 않도록 최신 값만 들고 있는다.
  const selectRef = useRef(onSelect);
  selectRef.current = onSelect;

  useEffect(() => {
    if (status !== "ready" || !containerRef.current || mapped.length === 0) {
      return;
    }
    const maps = window.google.maps;

    mapRef.current ??= new maps.Map(containerRef.current, {
      mapId: mapId(),
      // 컨트롤을 다 끄고 지도만 남긴다 — 폰 화면에서 버튼이 핀을 가린다.
      disableDefaultUI: true,
      gestureHandling: "greedy",
      zoom: 14,
      center: { lat: mapped[0].lat, lng: mapped[0].lng },
    });
    const map = mapRef.current;

    markersRef.current.forEach((m) => (m.map = null));
    markersRef.current = mapped.map((m) => {
      const marker = new maps.marker.AdvancedMarkerElement({
        map,
        position: { lat: m.lat, lng: m.lng },
        content: pinElement(m.order, m.activity.id === selectedId),
        title: `${m.order}. ${m.activity.title}`,
      });
      marker.addListener("click", () => selectRef.current(m.activity.id));
      return marker;
    });

    lineRef.current?.setMap(null);
    lineRef.current =
      mapped.length > 1
        ? new maps.Polyline({
            map,
            path: mapped.map((m) => ({ lat: m.lat, lng: m.lng })),
            strokeColor: "#8b00ff",
            strokeWeight: 3,
          })
        : null;

    // 핀이 전부 들어오게 범위를 맞춘다.
    if (mapped.length === 1) {
      map.setCenter({ lat: mapped[0].lat, lng: mapped[0].lng });
      map.setZoom(15);
    } else {
      const bounds = new maps.LatLngBounds();
      mapped.forEach((m) => bounds.extend({ lat: m.lat, lng: m.lng }));
      map.fitBounds(bounds, 24);
    }
  }, [status, mapped, selectedId]);

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
      aria-label="하루 동선 지도"
      className="bg-muted aspect-[8/5] w-full overflow-hidden rounded-lg border"
    />
  );
}
