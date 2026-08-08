import type { Activity } from "@/features/travel/api/activities";

/** 지도에 올릴 수 있는 일정 — 좌표 있는 장소가 붙은 것만. */
export interface MappedActivity {
  activity: Activity;
  lat: number;
  lng: number;
  /** 지도 표시 순서(1부터). 장소 없는 일정이 빠지므로 리스트 순번과 다를 수 있다. */
  order: number;
}

/**
 * 좌표가 있는 일정만 순서대로 남기고 번호를 <b>다시</b> 매긴다.
 *
 * <p>리스트 순번을 그대로 쓰면 지도에 1·3·4번만 뜬다 — 2번이 어디 갔는지 알 수 없고,
 * 지도에서 세는 순서와도 어긋난다.
 */
export function toMapped(activities: Activity[]): MappedActivity[] {
  return activities
    .filter((a) => a.place?.lat != null && a.place.lng != null)
    .map((activity, index) => ({
      activity,
      lat: activity.place!.lat!,
      lng: activity.place!.lng!,
      order: index + 1,
    }));
}
