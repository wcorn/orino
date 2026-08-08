/**
 * Google Places 데이터 출처 표기.
 *
 * <p><b>지도 없이 Places 데이터를 보여주는 화면에는 반드시 있어야 한다.</b>
 * (Places API 정책 — "If your application displays Places API data on a page or view
 * that does not also display a Google Map, you must show a Google logo with that data.")
 *
 * <p>로고 이미지 대신 <b>텍스트</b>를 쓴다. 정책이 공간이 좁을 때 텍스트를 허용하고
 * ("In cases where space is limited, the text Google Maps is acceptable"), 이 앱은
 * 폰 전용인 데다 <b>오프라인에서도 떠야</b> 해서 외부 이미지에 기댈 수 없다.
 */
export function GoogleAttribution() {
  return (
    <p className="text-muted-foreground py-2 text-center text-[11px]">
      Google Maps
    </p>
  );
}
