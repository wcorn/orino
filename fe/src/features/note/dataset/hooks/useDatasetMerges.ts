import { useQuery } from "@tanstack/react-query";

import { fetchDatasetMerges } from "../api/datasets";
import { datasetKeys } from "../queryKeys";

/**
 * 그 dataset의 병합 전체를 조회한다. 세로 병합은 앵커 행이 화면 밖이어도 덮인 행을 그려야 하므로,
 * 병합은 페이지가 아니라 dataset 단위로 통째 가져온다(병합은 sparse라 대개 적다).
 */
export function useDatasetMerges(datasetId: number) {
  return useQuery({
    queryKey: datasetKeys.merges(datasetId),
    queryFn: () => fetchDatasetMerges(datasetId),
    staleTime: 60 * 1000,
  });
}
