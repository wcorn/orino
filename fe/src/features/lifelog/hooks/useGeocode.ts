import { useQuery } from "@tanstack/react-query";

import { reverseGeocode, searchPlaces } from "../api/geocode";
import { lifelogKeys } from "../queryKeys";

/** 좌표 → 장소명. 좌표가 있을 때만 조회한다. */
export function useReverseGeocode(coords: { lat: number; lng: number } | null) {
  return useQuery({
    queryKey: coords
      ? lifelogKeys.geocodeReverse(coords.lat, coords.lng)
      : lifelogKeys.geocodeReverse(0, 0),
    queryFn: () => reverseGeocode(coords!.lat, coords!.lng),
    enabled: coords != null,
    staleTime: 60_000,
  });
}

/** 검색어 → 후보 장소. 2자 이상일 때만 조회한다. */
export function useSearchPlaces(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: lifelogKeys.geocodeSearch(trimmed),
    queryFn: () => searchPlaces(trimmed),
    enabled: trimmed.length >= 2,
    staleTime: 60_000,
  });
}
