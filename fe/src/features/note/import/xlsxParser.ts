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
  const workbook = XLSX.read(buffer, { type: "array" });
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
