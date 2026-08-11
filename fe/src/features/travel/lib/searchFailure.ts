import { isPlacesRejected } from "@/features/travel/api/places";

/** 검색이 실패한 이유. `null`이면 실패하지 않았다. */
export type SearchFailure = null | "error" | "rejected";

/**
 * 예외를 화면이 구분할 수 있는 값으로 바꾼다.
 *
 * <p>다섯 화면이 같은 기준을 써야 한다 — 한 화면만 "잠시 후 다시 시도"라고 말하면 사용자는
 * 그 화면에서만 계속 재시도하고, 그때마다 또 거절당한다.
 */
export function failureOf(error: unknown): SearchFailure {
  return isPlacesRejected(error) ? "rejected" : "error";
}
