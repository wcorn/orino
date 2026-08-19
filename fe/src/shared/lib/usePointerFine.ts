import { useEffect, useState } from "react";

/**
 * 주 입력 장치가 정밀 포인터(마우스·트랙패드)인가.
 *
 * <p>화면 <b>폭</b>이 아니라 <b>입력 장치</b>로 갈라야 하는 자리가 있다. 길게 눌러 무언가를
 * 시작하는 제스처가 그렇다 — 롱프레스는 터치의 관용구다. 손가락은 스크롤과 집어 올리기를
 * 구분할 방법이 그것뿐이지만, 마우스에는 그 문제가 없어서 배운 적 없는 동작이 된다.
 *
 * <p>폭으로 가르면 마우스를 붙인 태블릿이나 좁게 띄운 데스크톱 창에서 어긋난다.
 */
const FINE_POINTER_QUERY = "(pointer: fine)";

/**
 * 마우스·트랙패드면 true. 장치를 바꿔 꽂으면 따라 바뀐다.
 * matchMedia가 없는 환경(테스트 jsdom)은 <b>터치로 간주</b>한다(false) — 손가락 쪽이
 * 제약이 많은 입력이라, 모르면 그쪽에 맞추는 편이 안전하다.
 */
export function usePointerFine(): boolean {
  const [fine, setFine] = useState(
    () =>
      typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia(FINE_POINTER_QUERY).matches,
  );
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const mq = window.matchMedia(FINE_POINTER_QUERY);
    const handler = () => setFine(mq.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);
  return fine;
}
