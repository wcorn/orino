package ds.project.orino.planner.travel.route.client;

/**
 * 앱이 계산하는 이동수단(§1.3).
 *
 * <p>대중교통은 없다. 환승·요금·실시간 지연을 우리가 다시 그릴 이유가 없고, 명세도 처음부터
 * 구글 지도 딥링크로 넘기기로 되어 있다. (도쿄 구간 TRANSIT은 실제로 빈 결과를 준다.)
 */
public enum TravelMode {
    WALK,
    DRIVE
}
