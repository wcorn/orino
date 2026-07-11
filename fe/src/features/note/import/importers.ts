import { parseCsvSheets, parseXlsxSheets, type SheetData } from "./sheetParser";

/**
 * 데이터 Import 소스 레지스트리. v1은 파일 업로드 소스(Excel·CSV)만 활성. 이후 소스(Google Sheets 등)는
 * 이 목록에 `available: true` + `parse`만 얹으면 미리보기·표 삽입 파이프라인을 그대로 재사용한다.
 * 가져오기는 파일 업로드 기반만 지원한다(붙여넣기 미지원).
 */
export interface ImportSource {
  id: string;
  label: string;
  /** 파일 input accept(활성 파일 소스만). */
  accept?: string;
  /** v1 활성 여부. false면 "곧" 안내로 비활성 노출. */
  available: boolean;
  /** 파일을 시트별 2D로 파싱(활성 소스만). */
  parse?: (file: File) => Promise<SheetData[]>;
}

export const IMPORT_SOURCES: ImportSource[] = [
  {
    id: "xlsx",
    label: "Excel (.xlsx)",
    accept: ".xlsx",
    available: true,
    parse: parseXlsxSheets,
  },
  {
    id: "csv",
    label: "CSV (.csv)",
    accept: ".csv",
    available: true,
    parse: parseCsvSheets,
  },
  { id: "gsheets", label: "Google Sheets", available: false },
];

export const DEFAULT_IMPORT_SOURCE = "xlsx";
