import type { Editor } from "@tiptap/react";

import { parseCellTsv } from "../dataset/cellClipboard";
import { createDatasetFromTable } from "../import/datasetImport";

/**
 * 표에서 복사한 셀(TSV)을 노트 본문에 붙여넣을 때 → 그 조각으로 새 표(dataset)를 만들어
 * 커서 위치에 삽입한다. 헤더 없이 값만 있으므로 열 라벨은 기본값(열 N)으로 채워진다.
 * 빈 붙여넣기는 무시한다.
 */
export async function insertTableFromCells(
  editor: Editor,
  tsv: string,
): Promise<void> {
  const rows = parseCellTsv(tsv);
  if (rows.length === 0 || (rows.length === 1 && rows[0].join("") === "")) {
    return;
  }
  const datasetId = await createDatasetFromTable({ headers: null, rows });
  editor.chain().focus().insertDatasetTable({ datasetId }).run();
}
