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
  // 수식은 값과 따로 캐시한다 — 화면엔 값을 그리고, 저장할 땐 수식을 돌려줘야 한다.
  const [formulas, setFormulas] = useState<Map<number, Record<string, string>>>(
    () => new Map(),
  );
  const loadedPages = useRef<Set<number>>(new Set());
  const inflight = useRef<Set<number>>(new Set());
  // reset()이 스스로 재조회를 일으키기 위한 세대 번호. 호출자의 useEffect는 ensureRange를
  // 의존성으로 갖는데, 이 값이 바뀌면 ensureRange 정체성이 바뀌어 effect가 다시 돈다.
  const [generation, setGeneration] = useState(0);

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
            setFormulas((prev) => {
              const next = new Map(prev);
              pageData.rows.forEach((r) =>
                next.set(r.rowIndex, r.formulas ?? {}),
              );
              return next;
            });
          })
          .catch(() => {})
          .finally(() => inflight.current.delete(page));
      }
    },
    // generation은 본문에서 안 쓰지만, 바뀔 때마다 새 ensureRange를 만들어
    // 호출자의 effect를 다시 돌리려는 의도적 의존성이다(= reset 후 재조회).
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [datasetId, queryClient, generation],
  );

  /** 편집 낙관적 반영. */
  const setRowLocal = useCallback((index: number, cells: string[]) => {
    setCache((prev) => new Map(prev).set(index, cells));
  }, []);

  /** 서버가 돌려준 수식으로 갱신. */
  const setFormulasLocal = useCallback(
    (index: number, next: Record<string, string>) => {
      setFormulas((prev) => new Map(prev).set(index, next));
    },
    [],
  );

  /**
   * 캐시를 비우고 다시 받아 온다. 행 인덱스가 밀리거나(행 추가·삭제) 열 구성이 바뀌어
   * 캐시된 위치 배열이 더 이상 유효하지 않을 때(열 삭제) 쓴다.
   */
  const reset = useCallback(() => {
    loadedPages.current.clear();
    inflight.current.clear();
    setCache(new Map());
    setFormulas(new Map());
    queryClient.removeQueries({ queryKey: ["datasets", datasetId, "rows"] });
    setGeneration((g) => g + 1);
  }, [datasetId, queryClient]);

  const getRow = useCallback((index: number) => cache.get(index), [cache]);
  const getFormulas = useCallback(
    (index: number) => formulas.get(index) ?? {},
    [formulas],
  );

  return {
    getRow,
    getFormulas,
    ensureRange,
    setRowLocal,
    setFormulasLocal,
    reset,
  };
}
