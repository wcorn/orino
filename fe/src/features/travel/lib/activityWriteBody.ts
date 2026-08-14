import type { Activity, ActivityWriteRequest } from "../api/activities";

/**
 * 일정 수정 요청 바디를 <b>지금 상태 전체</b>로 채운다.
 *
 * <p>`PUT /travel/activities/{id}`는 부분 수정이 아니라 <b>전체 교체</b>다 — 보내지 않은
 * 필드는 서버가 지운다. 그래서 "날짜만 바꾼다" 같은 수정도 나머지 필드를 전부 실어 보내야
 * 하는데, 호출부마다 손으로 나열하면 <b>반드시 빠뜨린다</b>. 실제로 네 호출부가 전부
 * `placeId`를 빠뜨려 저장할 때마다 장소가 사라졌다(#1197).
 *
 * <p>바꾸려는 것만 {@link overrides}로 넘긴다. 나머지는 서버가 마지막으로 알려준 값
 * 그대로 되돌아간다.
 *
 * <p>`googlePlaceId`·`cityPlaceId`는 담지 않는다 — 검색 결과를 처음 담을 때 장소를
 * upsert 하려고 쓰는 입력이고, 이미 담긴 일정의 장소는 `placeId`로 가리켜져 있다.
 * 함께 보내면 서버가 같은 장소를 다시 조회한다(호출당 과금).
 */
export function activityWriteBodyFrom(
  activity: Activity,
  overrides: Partial<ActivityWriteRequest> = {},
): ActivityWriteRequest {
  return {
    title: activity.title,
    activityDate: activity.activityDate,
    startTime: activity.startTime,
    placeId: activity.place?.id ?? null,
    memo: activity.memo,
    url: activity.url,
    notifyEnabled: activity.notifyEnabled,
    notifyMinutes: activity.notifyMinutes,
    departureNotifyEnabled: activity.departureNotifyEnabled,
    ...overrides,
  };
}
