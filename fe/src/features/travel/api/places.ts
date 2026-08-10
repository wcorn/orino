import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 목적지 후보. 타임존·통화는 서버가 확정해서 준다 — 프론트가 좌표로 유추하지 않는다. */
export interface City {
  googlePlaceId: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  timezone: string;
  currency: string;
}

export interface PlaceSearchResult {
  /** 이미 담아 둔 장소면 내부 id, 아니면 null. */
  id: number | null;
  googlePlaceId: string;
  name: string;
  category: string | null;
  address: string | null;
  rating: number | null;
  /** 사진은 후속 작업(#1058)이라 지금은 항상 null. */
  lat: number | null;
  lng: number | null;
}

export interface PlaceDetail {
  id: number;
  googlePlaceId: string | null;
  name: string;
  address: string | null;
  lat: number | null;
  lng: number | null;
  category: string | null;
  phone: string | null;
  rating: number | null;
  /** Google 원본 JSON 문자열. 상세 화면에서만 쓴다. */
  openingHours: string | null;
  manualEntry: boolean;
}

export async function searchCities(q: string): Promise<City[]> {
  const { data } = await client.get<ApiEnvelope<City[]>>(
    "/travel/places/cities",
    { params: { q } },
  );
  return data.data;
}

/** `tripId`를 주면 그 여행 목적지 주변을 우선한다(필터가 아니라 편향). */
export async function searchPlaces(
  q: string,
  tripId?: number,
): Promise<PlaceSearchResult[]> {
  const { data } = await client.get<ApiEnvelope<PlaceSearchResult[]>>(
    "/travel/places/search",
    { params: tripId ? { q, tripId } : { q } },
  );
  return data.data;
}

export async function fetchPlaceDetail(placeId: number): Promise<PlaceDetail> {
  const { data } = await client.get<ApiEnvelope<PlaceDetail>>(
    `/travel/places/${placeId}`,
  );
  return data.data;
}

export interface ManualPlaceRequest {
  name: string;
  address?: string | null;
  /** `CITY`면 기준 도시로 쓸 수 있는 도시 장소가 된다. 생략하면 일반 장소(POI). */
  kind?: "CITY" | "POI";
  /** 도시일 때만. 검색이 못 찾는 도시는 사용자가 시간대·통화를 고른다. */
  timezone?: string;
  currency?: string;
  lat?: number | null;
  lng?: number | null;
}

/** 검색으로 안 나오는 곳(골목 카페, 숙소)을 직접 만든다. */
export async function createManualPlace(
  body: ManualPlaceRequest,
): Promise<PlaceDetail> {
  const { data } = await client.post<ApiEnvelope<PlaceDetail>>(
    "/travel/places",
    body,
  );
  return data.data;
}
