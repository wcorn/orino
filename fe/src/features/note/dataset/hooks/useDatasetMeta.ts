import { useQuery } from "@tanstack/react-query";

import { fetchDatasetMeta } from "../api/datasets";
import { datasetKeys } from "../queryKeys";

export function useDatasetMeta(datasetId: number) {
  return useQuery({
    queryKey: datasetKeys.meta(datasetId),
    queryFn: () => fetchDatasetMeta(datasetId),
    staleTime: 60 * 1000,
  });
}
