import { useQuery } from "@tanstack/react-query";

import { fetchPlaceDetail } from "../api/places";
import { travelKeys } from "../queryKeys";

/**
 * 장소 상세(영업시간·전화). 서버가 30일 캐시하고 지나면 알아서 갱신하므로(§4.7)
 * 프론트는 그냥 물어보면 된다 — 매번 외부를 부르는 게 아니다.
 */
export function usePlaceDetail(placeId: number | null) {
  return useQuery({
    queryKey: travelKeys.place(placeId ?? 0),
    queryFn: () => fetchPlaceDetail(placeId!),
    enabled: placeId !== null,
    staleTime: 60 * 60 * 1000,
  });
}
