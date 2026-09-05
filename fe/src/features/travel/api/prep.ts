import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/**
 * 준비 항목의 분류(v2.2 §11). <b>넷뿐이다.</b>
 *
 * <p>화면은 항상 이 순서로 그린다 — 서버가 내려주는 그룹 순서와 같다. 목록을 화면이 따로
 * 들고 있지 않는 이유가 그것이다: 두 벌이 되는 순간 다섯 번째 분류가 조용히 생긴다.
 */
export type PrepCategory = "DOCUMENT" | "BOOKING" | "BAG" | "TODO";

/**
 * 준비 항목 한 줄.
 *
 * <p><b>{@link dueDate}·{@link overdue}는 서버가 파생해 준 값이다.</b> 화면이 다시 계산하지
 * 않는다 — 기한 지남 판정의 기준은 「첫날 기준 도시의 오늘」인데, 그 시각은 브라우저에 없다.
 */
export interface PrepItemView {
  id: number;
  title: string;
  done: boolean;
  /** 짐에서만 쓴다. 다른 분류면 서버가 NULL로 떨어뜨린다(400이 아니다). */
  quantity: number | null;
  /** 출발 D−N. 절대 날짜를 저장하지 않는다(§12). */
  dueDaysBefore: number | null;
  dueDate: string | null;
  overdue: boolean;
  url: string | null;
  memo: string | null;
  displayOrder: number;
}

export interface PrepGroup {
  category: PrepCategory;
  total: number;
  done: number;
  items: PrepItemView[];
}

export interface PrepSummary {
  total: number;
  done: number;
  /** 전체 기준 하나. 사이드바 배지와 화면 상단이 같은 값을 읽는다. */
  overdueCount: number;
}

export interface PrepResponse extends PrepSummary {
  tripId: number;
  startDate: string;
  /** 출발까지 남은 일수. 첫날 기준 도시의 오늘로 센다. */
  dday: number;
  /** 항목이 하나도 없는 분류도 들어 있다. */
  groups: PrepGroup[];
}

/** 만들거나 고친 결과. 항목 하나와 <b>다시 센 집계</b>가 함께 온다. */
export interface PrepItemMutation {
  /** 항목이 실제로 들어간 분류. 생략하고 만들면 서버가 정하므로 화면은 이걸 보고 펼친다. */
  category: PrepCategory;
  item: PrepItemView;
  summary: PrepSummary;
}

/** 수정 요청이 「비우겠다」고 지목할 수 있는 칸. */
export type PrepField = "QUANTITY" | "DUE_DAYS_BEFORE" | "URL" | "MEMO";

export interface PrepCreateRequest {
  /** 생략하면 서버가 `TODO`로 넣는다(§11: 애매하면 할 일). */
  category?: PrepCategory;
  title: string;
  quantity?: number;
  dueDaysBefore?: number;
  url?: string;
  memo?: string;
}

/**
 * 부분 수정. <b>보낸 것만 바뀐다.</b>
 *
 * <p>값을 비우려면 {@link clear}에 칸 이름을 적는다 — 「안 보냄」과 「null로 바꿔 달라」를
 * 값만으로는 구별할 수 없다. 편집 시트에서 기한 칸을 비우는 것과 기한을 그대로 두는 것은
 * 다른 일이다.
 */
export interface PrepPatchRequest {
  category?: PrepCategory;
  title?: string;
  done?: boolean;
  quantity?: number;
  dueDaysBefore?: number;
  url?: string;
  memo?: string;
  clear?: PrepField[];
}

export async function fetchPrep(tripId: number): Promise<PrepResponse> {
  const { data } = await client.get<ApiEnvelope<PrepResponse>>(
    `/travel/trips/${tripId}/prep`,
  );
  return data.data;
}

export async function createPrepItem(
  tripId: number,
  body: PrepCreateRequest,
): Promise<PrepItemMutation> {
  const { data } = await client.post<ApiEnvelope<PrepItemMutation>>(
    `/travel/trips/${tripId}/prep/items`,
    body,
  );
  return data.data;
}

export async function updatePrepItem(
  itemId: number,
  body: PrepPatchRequest,
): Promise<PrepItemMutation> {
  const { data } = await client.patch<ApiEnvelope<PrepItemMutation>>(
    `/travel/prep/items/${itemId}`,
    body,
  );
  return data.data;
}

export async function deletePrepItem(itemId: number): Promise<void> {
  await client.delete(`/travel/prep/items/${itemId}`);
}
