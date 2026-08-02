/**
 * 화면에 떠 있는 표의 현재 내용을 "지금 당장(동기적으로)" 읽기 위한 등록소.
 *
 * 표 데이터는 노트 문서가 아니라 별도 dataset 리소스에 있어서, 복사할 때 문서만 봐서는
 * 내용을 알 수 없다. 클립보드 직렬화는 동기 함수라 그 자리에서 API를 부를 수도 없다.
 * 그래서 각 그리드가 마운트되는 동안 자기 스냅샷을 여기 등록해 두고, 복사 시점에 꺼내 쓴다.
 *
 * 아직 화면에 안 뜬(지연 로드 전) 행은 빈 칸으로 나온다 — 그래서 복사한 HTML에는
 * datasetId도 함께 실어, 앱 안에서 붙여넣을 땐 API로 원본 전체를 다시 읽는다.
 */
export interface TableSnapshot {
  name: string | null;
  /** 열 라벨. */
  headers: string[];
  /** 행 값. 아직 로드되지 않은 행은 빈 문자열로 채운다. */
  rows: string[][];
}

const registry = new Map<number, () => TableSnapshot>();

/** 그리드가 마운트되는 동안 자기 스냅샷 제공자를 등록한다. 반환값을 호출하면 해제된다. */
export function registerTableSnapshot(
  datasetId: number,
  get: () => TableSnapshot,
): () => void {
  registry.set(datasetId, get);
  return () => {
    // 같은 id로 다른 그리드가 이미 덮어썼다면 그쪽 것을 지우지 않는다.
    if (registry.get(datasetId) === get) registry.delete(datasetId);
  };
}

/** 화면에 떠 있는 표의 현재 내용. 없으면(언마운트된 표) null. */
export function getTableSnapshot(datasetId: number): TableSnapshot | null {
  return registry.get(datasetId)?.() ?? null;
}
