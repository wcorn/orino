import { useCallback, useEffect, useState } from "react";
import { Outlet } from "react-router-dom";

import { TransactionModal } from "./TransactionModal";
import { TransactionModalContext } from "./transactionModalContext";

/** 글자를 받는 자리에서 눌린 `N`은 단축키가 아니라 <b>입력</b>이다. */
function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return (
    target.tagName === "INPUT" ||
    target.tagName === "TEXTAREA" ||
    target.isContentEditable
  );
}

/**
 * 가계부 워크스페이스의 껍데기.
 *
 * <p>입력 모달 상태만 전역이다 — <b>`N`이 어디서든 열어야 하기 때문</b>이다. 나머지 상태
 * (필터·월 이동)는 URL 쿼리에 둔다: 새로고침·뒤로가기에서 화면과 사이드바가 어긋나지 않는다.
 *
 * <p>이 단축키를 가계부 라우트 안에만 두는 것이 중요하다. `AppLayout`에 붙이면 여행·일상
 * 화면에서 누른 `N`이 가계부 모달을 여는데, 그건 그 화면들의 동작 변경이다.
 */
export function LedgerLayout() {
  const [open, setOpen] = useState(false);
  const openTransactionModal = useCallback(() => setOpen(true), []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "n" && event.key !== "N") {
        return;
      }
      // 조합키는 브라우저·OS의 것이다(새 창 Cmd+N 등). 가로채지 않는다.
      if (event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      if (isTypingTarget(event.target)) {
        return;
      }
      event.preventDefault();
      setOpen(true);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return (
    <TransactionModalContext.Provider value={{ openTransactionModal }}>
      <Outlet />
      <TransactionModal open={open} onOpenChange={setOpen} />
    </TransactionModalContext.Provider>
  );
}
