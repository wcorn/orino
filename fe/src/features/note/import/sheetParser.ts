import * as XLSX from "xlsx";

/** 파싱된 한 시트. rows는 값만 담은 행 우선 2D 문자열 배열(직사각형). */
export interface SheetData {
  name: string;
  rows: string[][];
}

/**
 * .xlsx 파일을 클라이언트에서 파싱해 시트별 2D 문자열 배열로 정규화한다.
 * 값(표시 텍스트)만 가져오고 서식·수식·차트·병합은 무시한다(병합은 좌상단 값만).
 */
export async function parseXlsxSheets(file: File): Promise<SheetData[]> {
  const buffer = await file.arrayBuffer();
  return sheetsFromWorkbook(XLSX.read(buffer, { type: "array" }));
}

/**
 * .csv 파일을 파싱한다. CSV는 시트 개념이 없어 단일 시트로 반환한다.
 * UTF-8 텍스트로 읽으며 따옴표·구분자는 SheetJS가 처리한다.
 */
export async function parseCsvSheets(file: File): Promise<SheetData[]> {
  const text = await file.text();
  return sheetsFromWorkbook(XLSX.read(text, { type: "string" }));
}

/** 워크북(xlsx·csv 공통)을 시트별 2D 문자열 배열로 정규화한다. */
function sheetsFromWorkbook(workbook: XLSX.WorkBook): SheetData[] {
  return workbook.SheetNames.map((name) => {
    const sheet = workbook.Sheets[name];
    // header:1 → 행 우선 AoA, raw:false → 표시 텍스트(숫자/날짜 서식 반영), defval:"" → 빈 셀은 빈 문자열
    const aoa = XLSX.utils.sheet_to_json<unknown[]>(sheet, {
      header: 1,
      blankrows: false,
      defval: "",
      raw: false,
    });
    const rows = aoa.map((row) => row.map(cellToString));
    return { name, rows: normalizeRows(rows) };
  });
}

function cellToString(cell: unknown): string {
  if (cell === null || cell === undefined) return "";
  return String(cell);
}

/** 각 행을 최대 열 수로 패딩해 직사각형으로 만들고, 전 행이 빈 뒤쪽 열을 제거한다. */
function normalizeRows(rows: string[][]): string[][] {
  const maxCols = rows.reduce((max, row) => Math.max(max, row.length), 0);
  if (maxCols === 0) return [];

  const rectangular = rows.map((row) => {
    const padded = row.slice(0, maxCols);
    while (padded.length < maxCols) padded.push("");
    return padded;
  });

  let lastNonEmptyCol = -1;
  for (let c = 0; c < maxCols; c++) {
    if (rectangular.some((row) => row[c].trim() !== "")) lastNonEmptyCol = c;
  }
  if (lastNonEmptyCol < 0) return [];
  return rectangular.map((row) => row.slice(0, lastNonEmptyCol + 1));
}
