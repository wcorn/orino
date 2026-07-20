/**
 * 표 셀 복사 표식(MIME). 그리드에서 셀 범위를 복사할 때 text/plain(TSV)과 함께 이 타입으로도
 * 담아, 노트 본문에 붙여넣으면 "우리 표에서 복사한 셀"임을 구분해 그 조각으로 새 표를 만든다.
 * (외부/일반 텍스트 붙여넣기와 헷갈리지 않게 표식을 쓴다.)
 */
export const DATASET_CELLS_MIME = "text/x-orino-dataset-cells";

/** TSV(탭·줄바꿈) → 2차원 셀 배열. 끝 개행 1개는 무시한다. */
export function parseCellTsv(text: string): string[][] {
  return text
    .replace(/\r\n?/g, "\n")
    .replace(/\n$/, "")
    .split("\n")
    .map((line) => line.split("\t"));
}
