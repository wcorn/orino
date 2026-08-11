package ds.project.orino.planner.travel.external;

/**
 * 외부 API가 우리 호출을 <b>거절</b>했다(429·403).
 *
 * <p>클라이언트는 원래 모든 실패를 빈 값으로 떨어뜨린다 — 장소 검색이 안 된다고 일정 편집까지
 * 막을 이유가 없기 때문이다. 그런데 그러면 <b>"결과가 없다"와 "지금은 못 찾는다"가 같은 모양</b>이
 * 된다. 하드캡(#1151)에 걸린 사용자는 "검색 결과가 없어요"를 보고 검색어를 계속 바꾼다 —
 * 바꿀 때마다 또 거절당한다.
 *
 * <p>그래서 거절만 예외로 올린다. 나머지 실패(타임아웃·5xx·결과 0건)는 그대로 빈 값이다.
 *
 * <p><b>429와 403을 하나로 묶는다.</b> 429는 할당량이 확실하지만 403은 캡·키 제한·과금 비활성이
 * 섞여 있고, 구글 응답만으로는 갈라지지 않는다. 셋 다 우리 쪽에서 할 수 있는 일이 같다 —
 * 기다리거나 콘솔을 고치는 것이고, 사용자에게 할 말은 "지금은 안 되고 기존 정보는 그대로
 * 보인다"로 같다. 구분되는 것처럼 이름 붙이면 대시보드가 거짓말을 한다.
 */
public class ExternalApiRejectedException extends RuntimeException {

    public ExternalApiRejectedException(String message) {
        super(message);
    }
}
