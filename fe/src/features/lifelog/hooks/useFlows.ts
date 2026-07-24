import { useQuery } from "@tanstack/react-query";

import { fetchFlow, fetchFlows, type FlowStatus } from "../api/flows";
import { lifelogKeys } from "../queryKeys";

export function useFlows(status?: FlowStatus) {
  return useQuery({
    queryKey: lifelogKeys.flows(status),
    queryFn: () => fetchFlows(status),
  });
}

export function useFlow(id: number) {
  return useQuery({
    queryKey: lifelogKeys.flow(id),
    queryFn: () => fetchFlow(id),
  });
}
