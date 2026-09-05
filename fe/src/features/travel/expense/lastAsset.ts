const STORAGE_KEY = "orino.travel.lastExpenseAsset";

/**
 * 여행마다 <b>직전에 쓴 결제수단</b>(§6.1). 여행 중에는 같은 카드를 계속 쓰므로,
 * 매번 고르게 하면 30초 안에 끝나야 할 입력이 그만큼 길어진다.
 *
 * <p><b>서버에 두지 않는다.</b> 명세가 이 판정을 FE에 맡긴 이유가 있다 — 서버가 「직전」을
 * 추측하면 여행 밖 지출까지 끌어온다. 여기서 「직전」은 <b>이 여행의 이 시트에서 마지막으로
 * 고른 것</b>이고, 그건 이 기기만 아는 값이다.
 *
 * <p>저장이 막힌 환경(프라이빗 모드)에서도 입력 자체는 되어야 하므로 실패는 삼킨다 —
 * 기본값이 없으면 사용자가 한 번 고르면 그만이다.
 */
function read(): Record<string, number> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object"
      ? (parsed as Record<string, number>)
      : {};
  } catch {
    return {};
  }
}

export function getLastAsset(tripId: number): number | null {
  const value = read()[String(tripId)];
  return typeof value === "number" ? value : null;
}

export function rememberLastAsset(tripId: number, assetId: number): void {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ ...read(), [String(tripId)]: assetId }),
    );
  } catch {
    // 저장 실패는 다음 입력에서 기본값이 없는 것으로만 드러난다. 입력을 막지 않는다.
  }
}
