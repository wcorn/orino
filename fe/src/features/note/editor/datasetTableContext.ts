import { createContext, useContext } from "react";

export interface DatasetTableContextValue {
  /**
   * 표 삭제 요청 — 확인 다이얼로그를 먼저 띄우고, 확인할 때만 dataset 리소스 삭제와
   * 블록 제거(removeBlock)를 함께 처리한다. 취소하면 아무것도 바뀌지 않는다.
   * removeBlock은 되돌리기 불가로 블록을 지운다 — Cmd+Z로 블록만 되살아나 dataset이
   * 없는 고아 표("표를 불러오지 못했어요")가 생기던 문제를 막는다.
   */
  requestDeleteDataset: (datasetId: number, removeBlock: () => void) => void;
}

/**
 * datasetTable NodeView에 삭제 요청 핸들러를 주입하는 컨텍스트.
 * NoteEditor가 값을 채워 EditorContent를 이 Provider로 감싼다(NodeView는 상위 컨텍스트 상속).
 */
export const DatasetTableContext = createContext<DatasetTableContextValue>({
  requestDeleteDataset: () => {},
});

export function useDatasetTableContext(): DatasetTableContextValue {
  return useContext(DatasetTableContext);
}
