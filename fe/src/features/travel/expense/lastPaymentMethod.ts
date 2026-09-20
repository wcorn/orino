const STORAGE_KEY = "orino.travel.lastPaymentMethod";

/**
 * 여행마다 <b>직전에 쓴 결제수단</b>(경비 독립 §4.2). 여행 중에는 같은 카드를 계속 쓰므로,
 * 매번 적게 하면 30초 안에 끝나야 할 입력이 그만큼 길어진다.
 *
 * <p>예전에는 가계부 자산의 id를 기억했다. 자산 테이블이 따라오지 않았으므로 이제
 * <b>라벨 문자열</b>을 기억한다 — 「국민 체크」는 잔액을 볼 원장이 없는 지금, 그냥 말이다.
 *
 * <p><b>서버에 두지 않는다.</b> 서버가 「직전」을 추측하면 다른 여행에서 쓴 것까지 끌어온다.
 * 여기서 「직전」은 <b>이 여행의 이 시트에서 마지막으로 적은 것</b>이고, 그건 이 기기만
 * 아는 값이다.
 *
 * <p>저장이 막힌 환경(프라이빗 모드)에서도 입력 자체는 되어야 하므로 실패는 삼킨다 —
 * 기본값이 없으면 사용자가 한 번 적으면 그만이다.
 */
function read(): Record<string, string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object"
      ? (parsed as Record<string, string>)
      : {};
  } catch {
    return {};
  }
}

export function getLastPaymentMethod(tripId: number): string | null {
  const value = read()[String(tripId)];
  return typeof value === "string" && value !== "" ? value : null;
}

export function rememberLastPaymentMethod(
  tripId: number,
  paymentMethod: string | null,
): void {
  const trimmed = paymentMethod?.trim();
  if (!trimmed) return;
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ ...read(), [String(tripId)]: trimmed }),
    );
  } catch {
    // 저장 실패는 다음 입력에서 기본값이 없는 것으로만 드러난다. 입력을 막지 않는다.
  }
}
