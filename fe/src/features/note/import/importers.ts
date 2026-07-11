/**
 * 데이터 Import 소스 레지스트리. v1은 Excel(.xlsx)만 활성. 이후 소스(CSV/Google Sheets)는
 * 이 목록에 `available: true` + 파서만 얹으면 미리보기·표 삽입 파이프라인을 그대로 재사용한다.
 * 가져오기는 파일 업로드 기반만 지원한다(붙여넣기 미지원).
 */
export interface ImportSource {
  id: string;
  label: string;
  /** 파일 input accept(파일 기반 소스만). */
  accept?: string;
  /** v1 활성 여부. false면 "곧" 안내로 비활성 노출. */
  available: boolean;
}

export const IMPORT_SOURCES: ImportSource[] = [
  { id: "xlsx", label: "Excel (.xlsx)", accept: ".xlsx", available: true },
  { id: "csv", label: "CSV (.csv)", accept: ".csv", available: false },
  { id: "gsheets", label: "Google Sheets", available: false },
];

export const DEFAULT_IMPORT_SOURCE = "xlsx";
