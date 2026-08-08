package ds.project.orino.planner.travel.tools.dto;

/**
 * 날씨 아이콘. WMO 코드를 네 갈래로 줄인 것이다.
 *
 * <p>화면이 WMO 코드(0~99)를 알 필요가 없다. 코드를 그대로 넘기면 아이콘 매핑이 FE에 흩어지고,
 * 제공자를 바꾸면 그 매핑을 전부 다시 써야 한다. <b>여기서 한 번 끊는다.</b>
 */
public enum WeatherIcon {
    CLEAR,
    CLOUD,
    RAIN,
    SNOW;

    /**
     * WMO Weather interpretation code → 아이콘.
     *
     * <p>경계는 <a href="https://open-meteo.com/en/docs">Open-Meteo 문서</a>의 분류를 따른다.
     * 이슬비·소나기는 전부 비로 본다 — 우산을 챙기느냐가 유일한 관심사다.
     */
    public static WeatherIcon fromWmoCode(int code) {
        if (code == 0 || code == 1) {
            return CLEAR;
        }
        if (code == 2 || code == 3 || code == 45 || code == 48) {
            return CLOUD;
        }
        // 71~77 눈, 85~86 소낙눈.
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return SNOW;
        }
        // 51~67 이슬비·비·어는비, 80~82 소나기, 95~99 뇌우.
        return RAIN;
    }
}
