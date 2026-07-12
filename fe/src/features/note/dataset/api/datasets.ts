import { client } from "@/shared/api";

export interface DatasetColumn {
  key: string;
  label: string;
}

export interface DatasetMeta {
  id: number;
  columns: DatasetColumn[];
  rowCount: number;
}

export interface DatasetRow {
  rowIndex: number;
  cells: string[];
}

export interface RowsPage {
  rows: DatasetRow[];
  offset: number;
  limit: number;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function createDataset(
  columns: DatasetColumn[],
): Promise<DatasetMeta> {
  const { data } = await client.post<ApiEnvelope<DatasetMeta>>("/datasets", {
    columns,
  });
  return data.data;
}

/** Import 청크 — 행을 끝에 벌크 추가. */
export async function bulkAppendRows(
  datasetId: number,
  rows: string[][],
): Promise<DatasetMeta> {
  const { data } = await client.post<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/rows/bulk`,
    { rows },
  );
  return data.data;
}

export async function fetchDatasetMeta(
  datasetId: number,
): Promise<DatasetMeta> {
  const { data } = await client.get<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}`,
  );
  return data.data;
}

export async function fetchDatasetRows(
  datasetId: number,
  offset: number,
  limit: number,
): Promise<RowsPage> {
  const { data } = await client.get<ApiEnvelope<RowsPage>>(
    `/datasets/${datasetId}/rows`,
    { params: { offset, limit } },
  );
  return data.data;
}

export async function updateDatasetRow(
  datasetId: number,
  rowIndex: number,
  cells: string[],
): Promise<DatasetRow> {
  const { data } = await client.patch<ApiEnvelope<DatasetRow>>(
    `/datasets/${datasetId}/rows/${rowIndex}`,
    { cells },
  );
  return data.data;
}

export async function insertDatasetRow(
  datasetId: number,
  cells: string[],
  atIndex?: number,
): Promise<{ rowIndex: number }> {
  const { data } = await client.post<ApiEnvelope<{ rowIndex: number }>>(
    `/datasets/${datasetId}/rows`,
    { atIndex, cells },
  );
  return data.data;
}

export async function deleteDatasetRow(
  datasetId: number,
  rowIndex: number,
): Promise<void> {
  await client.delete(`/datasets/${datasetId}/rows/${rowIndex}`);
}

/** 데이터셋 삭제(노트에서 datasetTable 블록 제거 시). 행은 서버에서 cascade 삭제. */
export async function deleteDataset(datasetId: number): Promise<void> {
  await client.delete(`/datasets/${datasetId}`);
}
