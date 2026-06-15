import { useQuery } from "@tanstack/react-query";

import { fetchGoogleStatus } from "../api/googleApi";
import { googleKeys } from "../queryKeys";

export function useGoogleStatus() {
  return useQuery({
    queryKey: googleKeys.status,
    queryFn: fetchGoogleStatus,
    staleTime: 60 * 1000,
  });
}
