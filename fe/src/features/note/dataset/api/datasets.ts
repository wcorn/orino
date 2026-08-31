import { client } from "@/shared/api";
import { fileNameFromDisposition } from "@/shared/lib/download";

/** 열 푸터 요약 함수. 서버의 DatasetColumn.ALLOWED_SUMMARY와 같아야 한다. */
export type SummaryFn = "SUM" | "AVERAGE" | "COUNT" | "MIN" | "MAX";

/** 열 숫자 서식(표시 전용). 서버의 DatasetColumn.ALLOWED_FORMAT와 같아야 한다. */
export type NumberFormat =
  | "KRW"
  | "USD"
  | "JPY"
  | "THOUSANDS"
  | "DECIMAL1"
  | "DECIMAL2";

export interface DatasetColumn {
  key: string;
  label: string;
  /** 표시 너비(px). 없으면 기본 폭(균등 분배). */
  width?: number;
  /** 열 기본 정렬. 없으면 기본 정렬(left). 셀 정렬(CellStyle.align)이 있으면 그쪽이 덮는다. */
  align?: CellAlign;
  /** 열 푸터 요약 함수. 없으면 이 열엔 푸터 요약이 없다. 계산된 값은 DatasetMeta.summaries로 온다. */
  summary?: SummaryFn;
  /** 열 숫자 서식(표시 전용). 값·수식은 raw, 화면에만 이 서식으로 포맷한다. 없으면 그대로. */
  format?: NumberFormat;
  /** 허용값 목록(enum). 있으면 셀 편집에 드롭다운 제안(느슨 — 강제 아님). 없으면 자유 입력. */
  options?: string[];
}

/** 셀·열 정렬 값. 서버의 ALLOWED_ALIGN과 같아야 한다. */
export type CellAlign = "left" | "center" | "right";

/** 셀 세로 정렬 값. 서버의 ALLOWED_VALIGN과 같아야 한다. 세로 병합 셀 등에서 쓴다. */
export type CellValign = "top" | "middle" | "bottom";

/** 열 너비 하한(px). 서버의 DatasetColumn.MIN_WIDTH와 같아야 한다. */
export const MIN_COLUMN_WIDTH = 60;
/** 열 너비 상한(px). 서버의 DatasetColumn.MAX_WIDTH와 같아야 한다. */
export const MAX_COLUMN_WIDTH = 800;

/** 셀 배경 팔레트 토큰. 서버의 CellStyle.ALLOWED_BG·index.css의 --cell-bg-*와 같아야 한다. */
export const CELL_BG_TOKENS = [
  "red",
  "orange",
  "yellow",
  "green",
  "blue",
  "purple",
] as const;
export type CellBgToken = (typeof CELL_BG_TOKENS)[number];

/** 셀 서식(배경색·정렬). 지정 안 한 축은 없다(sparse). */
export interface CellStyle {
  bg?: CellBgToken;
  align?: CellAlign;
  valign?: CellValign;
}

/** 병합 요청의 span(앵커 기준 rowSpan×colSpan). (1,1)은 병합이 아니다. */
export interface MergeSpan {
  rowSpan: number;
  colSpan: number;
}

/**
 * 병합 하나의 표시형. 앵커를 **행 번호**로 가리킨다. 병합은 표시 오버레이라 cells는 직사각형
 * 그대로다 — 앵커를 span으로 넓게 그리고 덮인 칸은 렌더에서 숨긴다.
 *
 * 세로 병합은 앵커 행이 화면 밖이어도 덮인 행을 그려야 해, 병합은 행 단위가 아니라 dataset
 * 단위로 통째 조회한다(`GET /datasets/{id}/merges`).
 */
export interface MergeView {
  rowIndex: number;
  colKey: string;
  rowSpan: number;
  colSpan: number;
}

export interface DatasetMeta {
  id: number;
  /** 표 사용자용 이름. 없으면 무명. 노트 안 표 구별·표간 참조(#915)가 이름으로 지목한다. */
  name?: string;
  columns: DatasetColumn[];
  rowCount: number;
  /**
   * 푸터 요약 값 — summary 함수가 설정된 열의 key → 계산된 값. 값이 null이면 아직 계산 전이라
   * placeholder(`—`)로 그린다. (함수는 column.summary에, 값은 여기에. 값은 데이터마다 바뀐다.)
   */
  summaries?: Record<string, string | null>;
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
  /** 서식 있는 셀의 배경색·정렬(열 key → CellStyle). 서식 없는 셀은 없다. */
  styles: Record<string, CellStyle>;
}

export interface RowsPage {
  rows: DatasetRow[];
  offset: number;
  limit: number;
}

/**
 * 행 수정 결과. `edited`는 방금 고친 행, `affected`는 그 수정이 다른 행으로 번져(집계 등)
 * 값이 바뀐 행들(편집 행 제외, 번진 곳 없으면 빈 배열). 서버가 이미 다시 계산한 교차 행을
 * 페이지 재조회 없이 즉시 반영하려고 함께 내려준다. 서버의 `UpdateRowResponse`와 같아야 한다.
 */
export interface UpdateRowResult {
  edited: DatasetRow;
  affected: DatasetRow[];
  /**
   * 표간 참조로 전파가 다른 표에 번졌을 때 그 표 id들(R9 #915b). 이 응답엔 다른 표 행이 없으니,
   * 클라는 이 표들의 행 캐시를 무효화해 각 표 그리드가 다시 받게 한다. 없으면 빈 배열.
   */
  affectedDatasets: number[];
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
 * 열 추가. key와 기본 이름을 서버가 발급한다 — 클라이언트가 열 개수로 이름을 지으면
 * 열을 지운 뒤 중복이 생긴다. 기존 행은 서버에서 건드리지 않는다.
 *
 * `atIndex`를 주면 그 위치에 삽입하고, 없으면 끝에 추가한다.
 */
export async function addDatasetColumn(
  datasetId: number,
  atIndex?: number,
): Promise<DatasetMeta> {
  const { data } = await client.post<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns`,
    atIndex == null ? {} : { atIndex },
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

/** 열 기본 정렬 변경. 열 단위 표시 속성이라 행은 안 건드린다(셀 정렬이 있으면 그쪽이 덮는다). */
export async function setDatasetColumnAlign(
  datasetId: number,
  key: string,
  align: CellAlign,
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}/align`,
    { align },
  );
  return data.data;
}

/**
 * 열 푸터 요약 함수 설정/해제(멱등 교체). null이면 해제. 갱신된 메타(계산된 summaries 포함)를
 * 돌려준다 — 캐시에 바로 반영하면 푸터 값이 즉시 맞는다.
 */
export async function setColumnSummary(
  datasetId: number,
  key: string,
  summary: SummaryFn | null,
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}/summary`,
    { summary },
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
  // 표간 참조({표!열}) 이름 해석 맵: 표 이름 → 대상 datasetId. 노트 안 표는 FE만 알아서 보낸다.
  tableRefs?: Record<string, number>,
): Promise<UpdateRowResult> {
  const { data } = await client.patch<ApiEnvelope<UpdateRowResult>>(
    `/datasets/${datasetId}/rows/${rowIndex}`,
    { cells, tableRefs },
  );
  return data.data;
}

/**
 * 열 허용값 목록(enum) 설정/해제(멱등, 느슨). 빈 배열이면 해제. 서버가 정규화한 메타를 돌려준다.
 */
export async function setColumnOptions(
  datasetId: number,
  key: string,
  options: string[],
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}/options`,
    { options },
  );
  return data.data;
}

/**
 * 열 숫자 서식 설정/해제(멱등, 표시 전용). null이면 해제. 갱신된 메타를 돌려준다.
 */
export async function setColumnFormat(
  datasetId: number,
  key: string,
  format: NumberFormat | null,
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/columns/${key}/format`,
    { format },
  );
  return data.data;
}

/**
 * 표 이름 설정/해제(멱등). 빈 값이면 무명으로. 갱신된 메타를 돌려준다 — 캐시에 바로 반영.
 */
export async function setDatasetName(
  datasetId: number,
  name: string,
): Promise<DatasetMeta> {
  const { data } = await client.patch<ApiEnvelope<DatasetMeta>>(
    `/datasets/${datasetId}/name`,
    { name },
  );
  return data.data;
}

/** 채우기 핸들(세로 드래그) 요청. 서버의 `FillCellsRequest`와 같아야 한다. */
export interface FillCellsRequest {
  /** 채울 열들(소스·대상 공유). */
  cols: string[];
  /** 소스 행 범위(rowIndex, 포함). */
  srcR0: number;
  srcR1: number;
  /** 대상 행 범위(rowIndex, 포함). 소스와 겹치지 않고 바로 위/아래로 인접. */
  dstR0: number;
  dstR1: number;
}

/**
 * 채우기 핸들 — 소스 블록을 대상 행들에 세로로 타일링해 채운다. 값이 바뀐(대상 + 전파)
 * 행들을 돌려준다(행 번호 오름차순). 재조회 없이 캐시에 반영하면 된다.
 */
export async function fillCells(
  datasetId: number,
  req: FillCellsRequest,
): Promise<DatasetRow[]> {
  const { data } = await client.post<ApiEnvelope<DatasetRow[]>>(
    `/datasets/${datasetId}/cells/fill`,
    req,
  );
  return data.data;
}

/**
 * 셀 서식(배경색·정렬)을 통째로 교체한다. 빈 style이면 그 셀 서식을 지운다.
 * 값·수식과 무관한 표시 속성이라 cells는 안 바뀐다.
 */
export async function setCellStyle(
  datasetId: number,
  rowIndex: number,
  colKey: string,
  style: CellStyle,
): Promise<DatasetRow> {
  const { data } = await client.put<ApiEnvelope<DatasetRow>>(
    `/datasets/${datasetId}/rows/${rowIndex}/cells/${colKey}/style`,
    {
      bg: style.bg ?? null,
      align: style.align ?? null,
      valign: style.valign ?? null,
    },
  );
  return data.data;
}

/**
 * 여러 셀 서식을 한 번에 지정한다(선택 범위·행·열·표 전체 적용). 셀마다 서식을 통째로
 * 교체하며(각 셀의 보존할 속성은 호출자가 채운다), 영향받은 행들을 돌려준다.
 */
export async function setCellStylesBulk(
  datasetId: number,
  cells: Array<{ rowIndex: number; colKey: string; style: CellStyle }>,
): Promise<DatasetRow[]> {
  const { data } = await client.put<ApiEnvelope<DatasetRow[]>>(
    `/datasets/${datasetId}/cells/style`,
    {
      cells: cells.map((c) => ({
        rowIndex: c.rowIndex,
        colKey: c.colKey,
        bg: c.style.bg ?? null,
        align: c.style.align ?? null,
        valign: c.style.valign ?? null,
      })),
    },
  );
  return data.data;
}

/** 그 dataset의 병합 전체. 세로 병합은 앵커가 화면 밖이어도 덮인 행을 그려야 해 통째로 받는다. */
export async function fetchDatasetMerges(
  datasetId: number,
): Promise<MergeView[]> {
  const { data } = await client.get<ApiEnvelope<{ merges: MergeView[] }>>(
    `/datasets/${datasetId}/merges`,
  );
  return data.data.merges;
}

/**
 * 셀 병합. 앵커(rowIndex·colKey) 기준으로 rowSpan×colSpan 영역을 병합한다. 표시 오버레이라
 * 덮인 셀의 값은 보존되고 분할하면 되살아난다. 갱신된 병합 전체를 돌려준다.
 */
export async function setCellMerge(
  datasetId: number,
  rowIndex: number,
  colKey: string,
  span: MergeSpan,
): Promise<MergeView[]> {
  const { data } = await client.put<ApiEnvelope<{ merges: MergeView[] }>>(
    `/datasets/${datasetId}/rows/${rowIndex}/cells/${colKey}/merge`,
    { rowSpan: span.rowSpan, colSpan: span.colSpan },
  );
  return data.data.merges;
}

/** 병합 해제. 덮여 있던 셀 값은 그 자리에 되살아난다. 갱신된 병합 전체를 돌려준다. */
export async function deleteCellMerge(
  datasetId: number,
  rowIndex: number,
  colKey: string,
): Promise<MergeView[]> {
  const { data } = await client.delete<ApiEnvelope<{ merges: MergeView[] }>>(
    `/datasets/${datasetId}/rows/${rowIndex}/cells/${colKey}/merge`,
  );
  return data.data.merges;
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

/** 가져올 파일에 든 시트 하나. 고르기 전에 보여줄 만큼만 온다. */
export interface ImportSheetSummary {
  name: string;
  rowCount: number;
  columnCount: number;
  /** 머리글 후보 한 줄 + 본문 몇 줄. 첫 행 머리글 토글은 이걸로 화면에서 갈린다. */
  preview: string[][];
}

/** 가져오기 결과. */
export interface ImportResult {
  datasetId: number;
  rowCount: number;
  columnCount: number;
  formulasImported: number;
  /** 옮기지 못해 값으로 들어간 수식 수. 0이 아니면 화면이 말한다. */
  formulasAsValue: number;
}

/**
 * 파일에 어떤 시트가 들었는지 먼저 본다(#1310).
 *
 * 파싱은 서버가 한다 — 서식·수식·병합은 브라우저에서 읽을 수 없고, 읽을 수 있는 라이브러리는
 * 노트 화면 전체를 무겁게 만든다.
 */
export async function analyzeImportFile(
  file: File,
): Promise<ImportSheetSummary[]> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await client.post<ApiEnvelope<ImportSheetSummary[]>>(
    "/datasets/import/analyze",
    form,
  );
  return data.data;
}

/** 고른 시트를 표로 들인다. 값·수식·서식·병합·열 너비가 함께 온다. */
export async function importSheetAsDataset(
  file: File,
  sheet: string,
  firstRowAsHeader: boolean,
): Promise<ImportResult> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await client.post<ApiEnvelope<ImportResult>>(
    "/datasets/import",
    form,
    { params: { sheet, firstRowAsHeader } },
  );
  return data.data;
}

/**
 * 표를 .xlsx로 내려받는다(#1308).
 *
 * 파일이라 봉투(`ApiEnvelope`)가 없다 — 바이트가 그대로 온다. 파일 이름은 표 이름에서
 * 나오므로 서버가 정해 `Content-Disposition`에 실어 보낸다.
 */
export async function exportDatasetXlsx(
  datasetId: number,
): Promise<{ blob: Blob; fileName: string }> {
  // 바이트로 받아 Blob은 여기서 만든다. `responseType: "blob"`은 브라우저에선 같지만
  // 테스트(jsdom + XHR 인터셉터)에서 Node 22의 Blob 구현과 어긋나 터진다 — 타입을 응답에서
  // 그대로 옮겨 주는 편이 어느 쪽에서도 같은 결과다.
  const res = await client.get<ArrayBuffer>(`/datasets/${datasetId}/export`, {
    responseType: "arraybuffer",
  });
  const contentType =
    (res.headers["content-type"] as string | undefined) ??
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  return {
    blob: new Blob([res.data], { type: contentType }),
    fileName:
      fileNameFromDisposition(
        res.headers["content-disposition"] as string | undefined,
      ) ?? "dataset.xlsx",
  };
}
