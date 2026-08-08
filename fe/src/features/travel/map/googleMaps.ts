import { useEffect, useState } from "react";

/**
 * Maps JavaScript API 로더.
 *
 * <p><b>왜 구글 지도인가.</b> [ToS](https://cloud.google.com/maps-platform/terms)의
 * "No Use With Non-Google Maps"는 Places·Directions 콘텐츠를 비구글 지도와
 * <b>"with or near"</b> 쓰는 것을 금지한다 — 같은 화면에서 배치만 떼어놓는 것으로는
 * 풀리지 않는다. 일정 상세는 주소·영업시간·전화(Places)가, 지도 화면은 이동시간(Routes)이
 * 지도 옆에 붙어 있어 지도 자체를 구글로 바꾸는 것이 유일한 해결이다(#1102).
 *
 * <p>일상기록 지도는 Nominatim(OSM) 데이터라 이 제약과 무관하다 — leaflet 그대로 둔다.
 */

/** 빌드 시 주입되는 브라우저 키. 리퍼러 제한이 걸린 공개 키다(번들에 들어간다). */
const API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined;

/**
 * Advanced Marker에 필요한 Map ID.
 *
 * <p>없으면 마커가 아예 안 뜨므로 키와 함께 필수다. 스타일 없이 쓰려면 콘솔에서
 * 기본 Map ID 하나만 만들면 된다.
 */
const MAP_ID = import.meta.env.VITE_GOOGLE_MAPS_MAP_ID as string | undefined;

export type MapsStatus = "loading" | "ready" | "unavailable";

let loading: Promise<boolean> | null = null;

/** 스크립트를 한 번만 받는다. 지도 화면을 오갈 때마다 다시 받으면 안 된다. */
function load(): Promise<boolean> {
  if (loading) return loading;

  // 이미 올라와 있으면 키를 볼 것도 없다(다른 화면이 먼저 받아 뒀다).
  if (window.google?.maps) {
    loading = Promise.resolve(true);
    return loading;
  }
  if (!API_KEY) {
    // 키가 없으면 조용히 못 쓰는 상태로 둔다 — 지도가 없다고 앱이 죽으면 안 된다.
    loading = Promise.resolve(false);
    return loading;
  }

  loading = new Promise<boolean>((resolve) => {
    // 키가 거부되면(리퍼러 불일치·API 미활성) 구글은 예외 대신 이 콜백을 부른다.
    // 이걸 안 잡으면 스크립트는 "성공"인데 지도만 회색으로 남는다.
    window.gm_authFailure = () => resolve(false);

    const script = document.createElement("script");
    script.src =
      "https://maps.googleapis.com/maps/api/js" +
      `?key=${encodeURIComponent(API_KEY)}&libraries=marker&loading=async&v=weekly`;
    script.async = true;
    // 오프라인이면 여기로 온다. 화면은 이미 오프라인 안내를 따로 그린다(§S-05).
    script.onerror = () => resolve(false);
    script.onload = () => resolve(Boolean(window.google?.maps));
    document.head.appendChild(script);
  });
  return loading;
}

/** 테스트가 로더 상태를 되돌릴 수 있게 열어 둔다. 모듈 전역이라 테스트 사이에 샌다. */
export function resetGoogleMapsLoader(): void {
  loading = null;
}

export function mapId(): string | undefined {
  return MAP_ID;
}

/**
 * 지도를 쓸 수 있는지.
 *
 * <p>세 상태를 구분한다 — 로딩 중과 <b>못 쓰는 상태</b>는 화면이 다르게 다뤄야 한다.
 * 못 쓰는 이유(키 없음·오프라인·키 거부)는 사용자에게 구분해 봐야 소용없어 하나로 묶는다.
 */
export function useGoogleMaps(): MapsStatus {
  const [status, setStatus] = useState<MapsStatus>(() =>
    window.google?.maps ? "ready" : "loading",
  );

  useEffect(() => {
    let alive = true;
    void load().then((ok) => {
      if (alive) setStatus(ok ? "ready" : "unavailable");
    });
    return () => {
      alive = false;
    };
  }, []);

  return status;
}
