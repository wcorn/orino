import { useQuery } from "@tanstack/react-query";

import { fetchRowHeights } from "../api/datasets";
import { datasetKeys } from "../queryKeys";

/**
 * 그 dataset의 기본이 아닌 행 높이 전체를 조회한다. 세로 병합 오버레이가 앵커 밖 행의 누적 높이를
 * 알아야 하므로, 높이는 페이지가 아니라 dataset 단위로 통째 가져온다(대개 sparse).
 */
export function useDatasetRowHeights(datasetId: number) {
  return useQuery({
    queryKey: datasetKeys.rowHeights(datasetId),
    queryFn: () => fetchRowHeights(datasetId),
    staleTime: 60 * 1000,
  });
}
