import { client } from "@/shared/api";

/** 지오코딩 결과 한 건. placeName은 결과가 없으면 null. */
export interface GeocodePlace {
  placeName: string | null;
  lat: number;
  lng: number;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 좌표 → 장소명. */
export async function reverseGeocode(
  lat: number,
  lng: number,
): Promise<GeocodePlace> {
  const { data } = await client.get<ApiEnvelope<GeocodePlace>>(
    "/lifelog/geocode/reverse",
    { params: { lat, lng } },
  );
  return data.data;
}

/** 검색어 → 후보 장소. */
export async function searchPlaces(
  query: string,
  limit?: number,
): Promise<GeocodePlace[]> {
  const { data } = await client.get<ApiEnvelope<GeocodePlace[]>>(
    "/lifelog/geocode/search",
    { params: { q: query, limit } },
  );
  return data.data;
}
