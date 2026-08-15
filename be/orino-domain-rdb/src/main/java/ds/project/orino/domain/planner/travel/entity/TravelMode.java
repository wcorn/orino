package ds.project.orino.domain.planner.travel.entity;

/**
 * 이동수단의 <b>분류</b>(#1208).
 *
 * <p>여기 있는 값은 아이콘과 묶음에만 쓴다. 실제로 무엇을 타는지는
 * {@link TravelMove#getName()}에 자유 입력으로 적는다 — {@code TRAIN} + {@code 노조미 21호}.
 *
 * <p><b>나라 고유명을 넣지 않는다.</b> {@code SHINKANSEN}을 값으로 박으면 일본 밖에서 쓸 수
 * 없고, 다음엔 {@code TGV}·{@code KTX}를 넣어 달라는 요청이 이어진다. 분류를 범용으로 두고
 * 구체적인 이름을 아래로 내리면 그 줄이 끊긴다.
 *
 * <p>{@link #OTHER}가 있는 이유도 같다. 케이블카·툭툭·자가용 헬기 어느 것이든 적을 자리가
 * 있어야 한다 — 목록에 없다는 이유로 이동을 기록하지 못하면 계획 전체에 구멍이 난다.
 */
public enum TravelMode {
    WALK,
    BIKE,
    BUS,
    CAR,
    SUBWAY,
    TRAIN,
    FLIGHT,
    FERRY,
    OTHER
}
