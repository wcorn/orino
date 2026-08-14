import { describe, expect, it } from "vitest";

import type { Activity } from "@/features/travel/api/activities";

import { activityWriteBodyFrom } from "./activityWriteBody";

const SENSOJI: Activity = {
  id: 11,
  tripId: 1,
  title: "센소지",
  activityDate: "2026-10-24",
  startTime: "09:00",
  place: {
    id: 77,
    name: "센소지",
    address: "다이토구",
    lat: 35.7148,
    lng: 139.7967,
    cityName: "도쿄도",
    cityPlaceRef: "ChIJ_tokyo",
  },
  memo: "가미나리몬 앞에서 만나기",
  url: "https://www.senso-ji.jp",
  notifyEnabled: true,
  notifyMinutes: 30,
  departureNotifyEnabled: true,
  outOfBaseCity: false,
  canDepartureNotify: true,
  sortOrder: 0,
  log: null,
  hasLog: false,
};

describe("일정 수정 바디", () => {
  it("장소를 placeId로 실어 보낸다 — 빠뜨리면 서버가 장소를 지운다(#1197)", () => {
    expect(activityWriteBodyFrom(SENSOJI).placeId).toBe(77);
  });

  it("아무것도 안 바꾸면 지금 상태 그대로다", () => {
    expect(activityWriteBodyFrom(SENSOJI)).toEqual({
      title: "센소지",
      activityDate: "2026-10-24",
      startTime: "09:00",
      placeId: 77,
      memo: "가미나리몬 앞에서 만나기",
      url: "https://www.senso-ji.jp",
      notifyEnabled: true,
      notifyMinutes: 30,
      departureNotifyEnabled: true,
    });
  });

  it("날짜만 바꿔도 장소·메모·링크·알림설정은 그대로 간다", () => {
    const body = activityWriteBodyFrom(SENSOJI, { activityDate: "2026-10-25" });

    expect(body.activityDate).toBe("2026-10-25");
    expect(body.placeId).toBe(77);
    expect(body.startTime).toBe("09:00");
    expect(body.memo).toBe("가미나리몬 앞에서 만나기");
    expect(body.url).toBe("https://www.senso-ji.jp");
    expect(body.notifyEnabled).toBe(true);
    expect(body.notifyMinutes).toBe(30);
    expect(body.departureNotifyEnabled).toBe(true);
  });

  it("보관함으로 내려도 알림 설정은 남는다 — 날짜를 다시 주면 그대로 되살아난다", () => {
    // 서버는 날짜 없는 일정의 예약만 접는다. 여기서 플래그까지 꺼 버리면
    // 되돌려 놓았을 때 알림이 조용히 사라진 채로 남는다.
    const body = activityWriteBodyFrom(SENSOJI, { activityDate: null });

    expect(body.activityDate).toBeNull();
    expect(body.notifyEnabled).toBe(true);
    expect(body.notifyMinutes).toBe(30);
  });

  it("장소 없는 일정은 placeId가 null이다", () => {
    const body = activityWriteBodyFrom({ ...SENSOJI, place: null });

    expect(body.placeId).toBeNull();
  });

  it("googlePlaceId는 담지 않는다 — 이미 담긴 장소를 다시 조회하면 과금된다", () => {
    const body = activityWriteBodyFrom(SENSOJI);

    expect(body.googlePlaceId).toBeUndefined();
    expect(body.cityPlaceId).toBeUndefined();
  });
});
