import { createContext, useContext } from "react";

interface TransactionModalContextValue {
  openTransactionModal: () => void;
}

/**
 * 입력 모달을 여는 통로. <b>이 모듈에서 유일한 전역 상태</b>다 —
 * `N`이 어디서든 열어야 하기 때문이고, 나머지(필터·월 이동)는 전부 URL 쿼리에 둔다.
 *
 * <p>컴포넌트 파일과 갈라 두는 이유는 fast refresh다. 한 파일이 컴포넌트와 훅을 함께
 * 내보내면 편집할 때마다 상태가 통째로 날아간다.
 */
export const TransactionModalContext =
  createContext<TransactionModalContextValue>({
    openTransactionModal: () => {},
  });

/** 화면 어디서든 입력 모달을 여는 버튼이 쓴다(`입력 N`). */
export function useTransactionModal(): TransactionModalContextValue {
  return useContext(TransactionModalContext);
}
