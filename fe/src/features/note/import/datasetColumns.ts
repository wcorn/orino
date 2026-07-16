import type { DatasetColumn } from "@/features/note/dataset/api/datasets";

import type { NormalizedTable } from "./tableContent";

/** 툴바 "표 삽입" 기본 크기(열·행). */
export const DEFAULT_COLS = 3;
export const DEFAULT_ROWS = 3;

/** 정규화 표의 실제 열 개수(헤더/본문 중 최대 폭). */
export function columnCount(table: NormalizedTable): number {
  return Math.max(
    table.headers?.length ?? 0,
    ...table.rows.map((r) => r.length),
    0,
  );
}

/** 열 개수만큼 `열 N` 라벨의 기본 컬럼을 만든다. */
export function defaultColumns(cols: number): DatasetColumn[] {
  return Array.from({ length: cols }, (_, i) => ({
    key: `c${i}`,
    label: `열 ${i + 1}`,
  }));
}

/**
 * 정규화 표 → dataset 컬럼 메타. 표 삽입·Import·마이그레이션이 모두 이 규칙을
 * 공유한다(SSOT). key는 안정 식별자 `cN`, label은 헤더 값(없거나 빈 값이면 `열 N`).
 */
export function buildDatasetColumns(table: NormalizedTable): DatasetColumn[] {
  if (!table.headers) return defaultColumns(columnCount(table));
  return table.headers.map((label, i) => ({
    key: `c${i}`,
    label: label || `열 ${i + 1}`,
  }));
}
