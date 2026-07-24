import type { MomentCard } from "../api/types";

export interface GeoMoment {
  moment: MomentCard;
  lat: number;
  lng: number;
  /** 시간순 순번(1부터). */
  order: number;
}

/** 좌표가 있는 기록만 뽑아 시간순 번호를 매긴다(입력은 이미 흐름 순서). */
export function toGeoMoments(moments: MomentCard[]): GeoMoment[] {
  const result: GeoMoment[] = [];
  for (const moment of moments) {
    if (moment.lat != null && moment.lng != null) {
      result.push({
        moment,
        lat: moment.lat,
        lng: moment.lng,
        order: result.length + 1,
      });
    }
  }
  return result;
}
