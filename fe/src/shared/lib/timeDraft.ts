/**
 * 24시간제 시각 입력칸의 글자 해석(#1430). 컴포넌트는 `components/ui/time-input.tsx`.
 */

/**
 * 입력칸 글자 → 숫자 최대 4자리(시 2 + 분 2).
 *
 * <p>`9:30`처럼 콜론 앞이 한 자리면 그게 시다. 콜론 없이 `930`처럼 3으로 시작하는
 * 숫자를 치면 첫 자리를 시로 본다 — 30시는 없으니 `0930`이다.
 */
function toDigits(raw: string): string {
  const withColon = /^(\d{1,2}):(\d{0,2})/.exec(raw);
  if (withColon) {
    const hour = withColon[1].padStart(2, "0");
    return (hour + withColon[2]).slice(0, 4);
  }
  let digits = raw.replace(/\D/g, "");
  if (digits.length > 0 && digits[0] > "2") {
    digits = `0${digits}`;
  }
  return digits.slice(0, 4);
}

function toDisplay(digits: string): string {
  return digits.length > 2
    ? `${digits.slice(0, 2)}:${digits.slice(2)}`
    : digits;
}

/** 네 자리가 다 찼고 00:00–23:59 안이면 `HH:mm`, 아니면 null. */
function toTime(digits: string): string | null {
  if (digits.length !== 4) return null;
  const hour = Number(digits.slice(0, 2));
  const minute = Number(digits.slice(2));
  if (hour > 23 || minute > 59) return null;
  return `${digits.slice(0, 2)}:${digits.slice(2)}`;
}

/**
 * 입력칸에 친 글자를 해석한다. 화면에 보일 글자와, 완성됐으면 그 시각을 돌려준다.
 *
 * <p>`1430` · `14:30` · `930` · `9:30` 모두 받는다. 완성 전(`14:3`)이나 범위 밖(`25:00`)은
 * `time`이 null이다.
 */
export function parseTimeDraft(raw: string): {
  display: string;
  time: string | null;
} {
  const digits = toDigits(raw);
  return { display: toDisplay(digits), time: toTime(digits) };
}

/**
 * 칸을 떠날 때 덜 친 값을 마무리한다. 시만 쳤으면 정각이다(`9` → `09:00`).
 * 그 밖에 완성되지 않은 값은 null — 호출자가 원래 값으로 되돌린다.
 */
export function completeTimeDraft(raw: string): string | null {
  const digits = toDigits(raw);
  if (digits.length === 1 || digits.length === 2) {
    return toTime(`${digits.padStart(2, "0")}00`);
  }
  return toTime(digits);
}
