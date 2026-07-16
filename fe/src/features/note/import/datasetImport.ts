import {
  bulkAppendRows,
  createDataset,
  type DatasetColumn,
} from "@/features/note/dataset/api/datasets";

import type { NormalizedTable } from "./tableContent";

/** 벌크 업로드 청크 크기(BE 최대 2000행). */
const BULK_CHUNK = 1000;

/** 툴바 "표 삽입" 기본 크기(열·행). */
export const DEFAULT_COLS = 3;
export const DEFAULT_ROWS = 3;

function columnCount(table: NormalizedTable): number {
  return Math.max(
    table.headers?.length ?? 0,
    ...table.rows.map((r) => r.length),
    0,
  );
}

/** 열 개수만큼 `열 N` 라벨의 기본 컬럼을 만든다. */
function defaultColumns(cols: number): DatasetColumn[] {
  return Array.from({ length: cols }, (_, i) => ({
    key: `c${i}`,
    label: `열 ${i + 1}`,
  }));
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
    : defaultColumns(cols);

  const dataset = await createDataset(columns);
  for (let i = 0; i < table.rows.length; i += BULK_CHUNK) {
    await bulkAppendRows(dataset.id, table.rows.slice(i, i + BULK_CHUNK));
  }
  return dataset.id;
}

/**
 * 빈 dataset(열 라벨 `열 N`, 빈 셀 rows행)을 생성한다. 툴바 "표 삽입"용.
 * datasetId 반환.
 */
export async function createEmptyDataset(
  cols = DEFAULT_COLS,
  rows = DEFAULT_ROWS,
): Promise<number> {
  const dataset = await createDataset(defaultColumns(cols));
  if (rows > 0) {
    const emptyRows = Array.from({ length: rows }, () =>
      Array.from({ length: cols }, () => ""),
    );
    await bulkAppendRows(dataset.id, emptyRows);
  }
  return dataset.id;
}
