import { client } from "@/shared/api";

export interface DatasetColumn {
  key: string;
  label: string;
  /** 표시 너비(px). 없으면 기본 폭(균등 분배). */
  width?: number;
}

/** 열 너비 하한(px). 서버의 DatasetColumn.MIN_WIDTH와 같아야 한다. */
export const MIN_COLUMN_WIDTH = 60;
/** 열 너비 상한(px). 서버의 DatasetColumn.MAX_WIDTH와 같아야 한다. */
export const MAX_COLUMN_WIDTH = 800;

export interface DatasetMeta {
  id: number;
  columns: DatasetColumn[];
  rowCount: number;
}

export interface DatasetRow {
  /**
   * 행의 안정적 식별자. rowIndex는 삽입·삭제 때마다 밀리지만 id는 바뀌지 않는다.
   * 수식이 다른 행을 참조할 때 묶을 대상(아직 사용처 없음).
   */
  id: number;
  rowIndex: number;
  /** 계산된 값. 수식 셀이면 계산 결과가 들어 있다. */
  cells: string[];
  /**
   * 수식 있는 셀의 원본(열 key → 표시형 수식). 수식 없는 셀은 없다.
   *
   * 행을 수정할 땐 수식 셀에 이걸 그대로 돌려줘야 한다 — 계산된 값을 돌려주면
   * 서버가 사용자가 직접 입력한 것으로 보고 수식을 지운다.
   */
  formulas: Record<string, string>;
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

/**
 * 열 추가(끝에). key와 기본 이름을 서버가 발급한다 — 클라이언트가 열 개수로 이름을 지으면
 * 열을 지운 뒤 중복이 생긴다. 기존 행은 서버에서 건드리지 않는다.
 */
export async function addDatasetColumn(
  datasetId: number,
): Promise<DatasetMeta> {
  const { data } = await client.post<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns`,
    {},
  );
  return data.data;
}

/**
 * 열 순서 변경. 전체 순서를 보내며 현재 열 집합과 정확히 같아야 한다.
 * cells가 key 맵이라 서버는 columns_json 순서만 바꾸고 행은 건드리지 않는다.
 */
export async function reorderDatasetColumns(
  datasetId: number,
  keys: string[],
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/order`,
    { keys },
  );
  return data.data;
}

/** 열 너비 변경(px). 열 단위 표시 속성이라 행은 안 건드린다. */
export async function resizeDatasetColumn(
  datasetId: number,
  key: string,
  width: number,
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}/width`,
    { width },
  );
  return data.data;
}

/** 열 너비 초기화 — 기본 폭으로 되돌린다. */
export async function resetDatasetColumnWidth(
  datasetId: number,
  key: string,
): Promise<DatasetMeta> {
  const { data } = await client.delete<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}/width`,
  );
  return data.data;
}

/** 열 삭제. 마지막 열은 지울 수 없다(400). */
export async function deleteDatasetColumn(
  datasetId: number,
  key: string,
): Promise<DatasetMeta> {
  const { data } = await client.delete<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}`,
  );
  return data.data;
}

/** 열 이름 변경. key는 cells 맵의 주소라 바뀌지 않고 label만 바뀐다. */
export async function renameDatasetColumn(
  datasetId: number,
  key: string,
  label: string,
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}`,
    { label },
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
