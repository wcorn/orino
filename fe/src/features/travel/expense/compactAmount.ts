/**
 * 「41.2만」. 경비 화면이 큰 숫자를 한 줄에 여러 개 늘어놓는 자리에서 쓴다.
 *
 * <p>`412,000`을 그대로 쓰면 「80만 중 41.2만」 한 줄이 「800,000 중 412,000」이 되어,
 * 읽으려면 자릿수를 세어야 한다. 예산 카드는 <b>비율을 눈으로 잡는</b> 자리라 그게 곧 실패다.
 *
 * <p>만 미만은 콤마 그대로 둔다 — 「0.4만」은 4,000보다 읽기 어렵다.
 * 소수는 한 자리까지만 남기고, 딱 떨어지면 「80만」처럼 뗀다.
 */
export function formatCompactAmount(amount: number): string {
  const abs = Math.abs(amount);
  if (abs < 10_000) {
    return amount.toLocaleString("ko-KR");
  }
  const man = amount / 10_000;
  // toFixed(1)이 만드는 `.0`은 떼고, 억 단위여도 만으로 읽는다 — 여행 경비에서
  // 「1.2억」이 나올 일이 없고, 단위를 섞으면 두 숫자를 비교할 수 없다.
  const rounded = Math.round(man * 10) / 10;
  return `${rounded.toLocaleString("ko-KR", { maximumFractionDigits: 1 })}만`;
}
