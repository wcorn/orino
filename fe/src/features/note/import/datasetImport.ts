import {
  bulkAppendRows,
  createDataset,
  type DatasetColumn,
} from "@/features/note/dataset/api/datasets";

import type { NormalizedTable } from "./tableContent";

/** 이 셀 수를 넘으면 native 표 대신 데이터셋 그리드 블록으로 삽입한다. */
export const DATASET_CELL_THRESHOLD = 2000;

/** 벌크 업로드 청크 크기(BE 최대 2000행). */
const BULK_CHUNK = 1000;

function columnCount(table: NormalizedTable): number {
  return Math.max(
    table.headers?.length ?? 0,
    ...table.rows.map((r) => r.length),
    0,
  );
}

/**
 * 표가 native Tiptap 표 상한(열/행)이나 셀 임계치를 넘어 데이터셋 그리드 블록으로
 * 가야 하는지. 소형은 가벼운 native 표, 대형은 별도 dataset + 가상화 그리드.
 */
export function shouldUseDataset(
  table: NormalizedTable,
  maxCols: number,
  maxRows: number,
): boolean {
  const cols = columnCount(table);
  const rows = table.rows.length;
  const cells = cols * (rows + (table.headers ? 1 : 0));
  return cols > maxCols || rows > maxRows || cells > DATASET_CELL_THRESHOLD;
}

/** 정규화 표 → dataset 생성 + 행 벌크 업로드(청크). datasetId 반환. */
export async function createDatasetFromTable(
  table: NormalizedTable,
): Promise<number> {
  const cols = columnCount(table);
  const columns: DatasetColumn[] = table.headers
    ? table.headers.map((label, i) => ({
        key: `c${i}`,
        label: label || `열 ${i + 1}`,
      }))
    : Array.from({ length: cols }, (_, i) => ({
        key: `c${i}`,
        label: `열 ${i + 1}`,
      }));

  const dataset = await createDataset(columns);
  for (let i = 0; i < table.rows.length; i += BULK_CHUNK) {
    await bulkAppendRows(dataset.id, table.rows.slice(i, i + BULK_CHUNK));
  }
  return dataset.id;
}
