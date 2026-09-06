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
  /**
   * 분류 안의 묶음 이름. `null`이면 묶음 없음이다(#1358).
   *
   * <p>묶음(`PrepSection`)이 이미 갖고 있는 값을 항목도 든다. 편집 시트는 항목 하나만 들고
   * 열리고 수정 요청도 항목 단위라, 「지금 무슨 묶음인가」를 항목이 말할 수 있어야 한다.
   */
  sectionLabel: string | null;
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

/**
 * 분류 안의 묶음 하나(#1358).
 *
 * <p>`label`이 `null`인 묶음은 「묶음 없음」이고 <b>항상 맨 앞</b>이다. 이름 붙은 묶음이
 * 하나도 없는 분류는 이 묶음 하나만 오고, 그때 화면은 소제목을 그리지 않는다 — 아무것도
 * 안 나눈 사람에게 「묶음 없음」이라는 줄 하나가 늘어나는 것이 이 기능의 비용이 되면 안 된다.
 */
export interface PrepSection {
  label: string | null;
  total: number;
  done: number;
  items: PrepItemView[];
}

export interface PrepGroup {
  category: PrepCategory;
  total: number;
  done: number;
  /** 항목이 없는 분류는 빈 배열이다 — 빈 묶음은 내려오지 않는다. */
  sections: PrepSection[];
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
export type PrepField =
  | "QUANTITY"
  | "DUE_DAYS_BEFORE"
  | "URL"
  | "MEMO"
  | "SECTION_LABEL";

export interface PrepCreateRequest {
  /** 생략하면 서버가 `TODO`로 넣는다(§11: 애매하면 할 일). */
  category?: PrepCategory;
  title: string;
  /** 묶음 이름. 생략하면 묶음 없음이다. */
  sectionLabel?: string;
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
  /** 옮겨 갈 묶음. 「묶음에서 빼 달라」는 값이 아니라 `clear`로 적는다. */
  sectionLabel?: string;
  quantity?: number;
  dueDaysBefore?: number;
  url?: string;
  memo?: string;
  clear?: PrepField[];
}

/**
 * 묶음이 없던 시절의 응답(#1358 이전). <b>서비스워커 캐시에 남아 있을 수 있다</b> —
 * `/api/travel/*`는 오프라인 조회를 위해 NetworkFirst로 캐시되므로, 기내에서 목록을 열면
 * 그때 꺼내지는 것이 이 모양일 수 있다(#1361).
 */
interface LegacyPrepGroup extends Omit<PrepGroup, "sections"> {
  sections?: PrepSection[];
  items?: PrepItemView[];
}

interface RawPrepResponse extends Omit<PrepResponse, "groups"> {
  groups: LegacyPrepGroup[];
}

/**
 * 옛 모양 응답을 지금 화면이 읽는 모양으로 맞춘다(#1361).
 *
 * <p><b>화면이 아니라 여기서 한 번만 한다.</b> 컴포넌트마다 `sections ?? []`를 흩어 놓으면
 * 빠뜨린 한 곳에서 흰 화면이 되고, 그게 어느 화면인지는 배포하고 나서야 안다.
 *
 * <p>묶음이 없던 응답은 전부 「묶음 없음」 하나로 감싼다 — 그때 화면은 소제목을 그리지
 * 않으므로, 사용자에게는 캐시를 읽었다는 사실이 보이지 않는다.
 */
function withSections(response: RawPrepResponse): PrepResponse {
  return {
    ...response,
    groups: response.groups.map((group) => {
      if (group.sections) return { ...group, sections: group.sections };
      const items = (group.items ?? []).map((item) => ({
        ...item,
        sectionLabel: item.sectionLabel ?? null,
      }));
      return {
        ...group,
        sections:
          items.length === 0
            ? []
            : [
                {
                  label: null,
                  total: group.total,
                  done: group.done,
                  items,
                },
              ],
      };
    }),
  };
}

export async function fetchPrep(tripId: number): Promise<PrepResponse> {
  const { data } = await client.get<ApiEnvelope<RawPrepResponse>>(
    `/travel/trips/${tripId}/prep`,
  );
  return withSections(data.data);
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
