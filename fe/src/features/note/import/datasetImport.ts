import {
  bulkAppendRows,
  createDataset,
} from "@/features/note/dataset/api/datasets";

import {
  buildDatasetColumns,
  DEFAULT_COLS,
  DEFAULT_ROWS,
  defaultColumns,
} from "./datasetColumns";
import type { NormalizedTable } from "./tableContent";

/** 벌크 업로드 청크 크기(BE 최대 2000행). */
const BULK_CHUNK = 1000;

/** 정규화 표 → dataset 생성 + 행 벌크 업로드(청크). datasetId 반환. */
export async function createDatasetFromTable(
  table: NormalizedTable,
): Promise<number> {
  const dataset = await createDataset(buildDatasetColumns(table));
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
