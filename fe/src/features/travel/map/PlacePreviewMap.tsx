import { useEffect, useRef } from "react";

import { mapId, useGoogleMaps } from "./googleMaps";

/**
 * 장소 하나짜리 미리보기(§S-07). 조작하지 않는다 — 드래그·줌을 열어두면 폼 안에서
 * 스크롤을 잡아먹는다. "여기가 어디쯤인지"만 보여주면 된다.
 *
 * <p>구글 지도를 쓴다. 이 블록은 주소·영업시간·전화(Places 콘텐츠) 바로 옆이라 비구글
 * 지도를 쓸 수 없다 — 약관이 금지하는 것은 "on"이 아니라 <b>"with or near"</b>다(#1102).
 */
export function PlacePreviewMap({ lat, lng }: { lat: number; lng: number }) {
  const status = useGoogleMaps();
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<google.maps.Map | null>(null);
  const markerRef = useRef<google.maps.marker.AdvancedMarkerElement | null>(
    null,
  );

  useEffect(() => {
    if (status !== "ready" || !containerRef.current) return;
    const maps = window.google.maps;

    mapRef.current ??= new maps.Map(containerRef.current, {
      mapId: mapId(),
      disableDefaultUI: true,
      // 폼 안이라 제스처를 막는다. 지도가 스크롤을 삼키면 폼을 못 내린다.
      gestureHandling: "none",
      keyboardShortcuts: false,
      zoom: 15,
      center: { lat, lng },
    });
    mapRef.current.setCenter({ lat, lng });

    markerRef.current ??= new maps.marker.AdvancedMarkerElement({
      map: mapRef.current,
    });
    markerRef.current.position = { lat, lng };
  }, [status, lat, lng]);

  useEffect(() => {
    return () => {
      if (markerRef.current) markerRef.current.map = null;
      markerRef.current = null;
      mapRef.current = null;
    };
  }, []);

  if (status === "unavailable") {
    // 장소 정보는 위에 이미 다 있다. 지도만 조용히 빠진다.
    return null;
  }

  return (
    <div
      ref={containerRef}
      role="img"
      aria-label="장소 위치 지도"
      className="bg-muted h-[120px] w-full overflow-hidden rounded-lg"
    />
  );
}
