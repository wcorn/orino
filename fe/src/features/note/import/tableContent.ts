/** 정규화된 표 입력. headers가 있으면 헤더 행으로, 없으면 전부 본문. */
export interface NormalizedTable {
  headers: string[] | null;
  rows: string[][];
}
