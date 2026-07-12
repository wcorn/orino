import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";

import { fetchDatasetRows } from "../api/datasets";
import { datasetKeys } from "../queryKeys";

/** 가상화 스크롤에 맞춰 페이지 단위로 행을 지연 로드하는 크기. */
export const DATASET_PAGE_SIZE = 100;

/**
 * 데이터셋 행을 페이지 단위로 지연 로드해 rowIndex→cells 캐시로 제공한다.
 * 가상화 그리드가 보이는 범위를 {@link ensureRange}로 알리면 해당 페이지만 fetch한다.
 */
export function useDatasetRows(datasetId: number) {
  const queryClient = useQueryClient();
  const [cache, setCache] = useState<Map<number, string[]>>(() => new Map());
  const loadedPages = useRef<Set<number>>(new Set());
  const inflight = useRef<Set<number>>(new Set());

  const ensureRange = useCallback(
    (start: number, end: number) => {
      const firstPage = Math.max(0, Math.floor(start / DATASET_PAGE_SIZE));
      const lastPage = Math.floor(end / DATASET_PAGE_SIZE);
      for (let page = firstPage; page <= lastPage; page++) {
        if (loadedPages.current.has(page) || inflight.current.has(page)) {
          continue;
        }
        inflight.current.add(page);
        const offset = page * DATASET_PAGE_SIZE;
        queryClient
          .fetchQuery({
            queryKey: datasetKeys.rows(datasetId, offset, DATASET_PAGE_SIZE),
            queryFn: () =>
              fetchDatasetRows(datasetId, offset, DATASET_PAGE_SIZE),
            staleTime: 60 * 1000,
          })
          .then((pageData) => {
            loadedPages.current.add(page);
            setCache((prev) => {
              const next = new Map(prev);
              pageData.rows.forEach((r) => next.set(r.rowIndex, r.cells));
              return next;
            });
          })
          .catch(() => {})
          .finally(() => inflight.current.delete(page));
      }
    },
    [datasetId, queryClient],
  );

  /** 편집 낙관적 반영. */
  const setRowLocal = useCallback((index: number, cells: string[]) => {
    setCache((prev) => new Map(prev).set(index, cells));
  }, []);

  /** 행 추가/삭제로 인덱스가 밀리면 전체 재로드. */
  const reset = useCallback(() => {
    loadedPages.current.clear();
    inflight.current.clear();
    setCache(new Map());
    queryClient.removeQueries({ queryKey: ["datasets", datasetId, "rows"] });
  }, [datasetId, queryClient]);

  const getRow = useCallback((index: number) => cache.get(index), [cache]);

  return { getRow, ensureRange, setRowLocal, reset };
}
