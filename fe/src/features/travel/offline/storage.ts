import { TRAVEL_CACHE } from "@/shared/lib/cacheNames";

export { TRAVEL_CACHE };

export interface StorageUsage {
  /** 이 출처가 쓰는 총 바이트(캐시·IndexedDB 등 전부). */
  usage: number;
  /** 브라우저가 허용한 상한. 모르면 null이다 — 없는 값을 지어내지 않는다. */
  quota: number | null;
}

/**
 * 저장 용량 실측.
 *
 * <p>서버가 알 수 없는 값이라 여기서만 구할 수 있다. <b>지원하지 않으면 null</b>을 돌려준다 —
 * 0으로 꾸미면 "아무것도 안 쌓였다"로 읽혀 정반대의 오해를 만든다.
 */
export async function estimateStorage(): Promise<StorageUsage | null> {
  if (!navigator.storage?.estimate) return null;
  try {
    const { usage, quota } = await navigator.storage.estimate();
    if (usage === undefined) return null;
    return { usage, quota: quota ?? null };
  } catch {
    return null;
  }
}

/**
 * 캐시에 담긴 여행 응답 건수.
 *
 * <p>바이트만 보여주면 크기가 큰지 작은지 감이 안 온다. "몇 건이 저장돼 있다"가
 * 비행기 모드에서 뭘 볼 수 있는지에 더 가깝다.
 */
export async function countCachedResponses(): Promise<number | null> {
  if (typeof caches === "undefined") return null;
  try {
    if (!(await caches.has(TRAVEL_CACHE))) return 0;
    const cache = await caches.open(TRAVEL_CACHE);
    return (await cache.keys()).length;
  } catch {
    return null;
  }
}

/**
 * 여행 데이터 캐시만 비운다.
 *
 * <p><b>앱 셸(precache)은 남긴다.</b> 셸까지 지우면 오프라인에서 앱이 아예 열리지 않는다 —
 * 용량을 줄이려다 기능을 없애는 셈이다. 셸은 배포마다 자동으로 갈린다.
 *
 * @return 실제로 지울 캐시가 있었는지
 */
export async function clearTravelCache(): Promise<boolean> {
  if (typeof caches === "undefined") return false;
  return caches.delete(TRAVEL_CACHE);
}

const UNITS = ["B", "KB", "MB", "GB"];

/** 사람이 읽는 크기. 1024 기준이고 소수는 한 자리까지만 — 정밀도가 목적이 아니다. */
export function formatBytes(bytes: number): string {
  if (bytes < 1) return "0 B";
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit += 1;
  }
  // B는 소수점이 의미 없다.
  const digits = unit === 0 || value >= 100 ? 0 : 1;
  return `${value.toFixed(digits)} ${UNITS[unit]}`;
}
