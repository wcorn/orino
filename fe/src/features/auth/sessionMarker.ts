/**
 * 이 기기에서 로그인한 적이 있다는 표시.
 *
 * <p><b>비밀이 아니다.</b> 토큰도 사용자 정보도 아니고 "여기서 로그인한 적 있음"이라는 사실
 * 하나뿐이라, 저장한다고 새로 드러나는 것이 없다. 액세스 토큰을 메모리에만 두는 결정은
 * 그대로 둔다.
 *
 * <p>이게 필요한 이유: 오프라인에서 새로고침하면 토큰이 사라지고 재발급도 못 한다. 그때
 * "로그인한 적 없는 사람"과 "지금 네트워크가 없을 뿐인 사람"을 구분할 근거가 이것뿐이다.
 * 없으면 캐시에 일정이 다 있어도 로그인 화면만 보게 된다(#1095).
 */
const KEY = "orino.session";

/** localStorage가 막힌 브라우저(사파리 프라이빗 등)에서도 앱이 죽으면 안 된다. */
function safely<T>(fn: () => T, fallback: T): T {
  try {
    return fn();
  } catch {
    return fallback;
  }
}

export function markSession(): void {
  safely(() => localStorage.setItem(KEY, "1"), undefined);
}

/**
 * 표시를 지운다. <b>서버가 거절했을 때만</b> 부른다 — 네트워크가 안 닿은 것은 로그아웃이
 * 아니다. 그 둘을 섞으면 비행기 모드에서 로그아웃당한다.
 */
export function clearSession(): void {
  safely(() => localStorage.removeItem(KEY), undefined);
}

export function hadSession(): boolean {
  return safely(() => localStorage.getItem(KEY) === "1", false);
}
