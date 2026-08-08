package ds.project.orino.planner.travel.place.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Google Places 호출 경계. 캐시는 상위 서비스가 흡수하므로 여기선 순수 호출만 한다.
 *
 * <p>인터페이스로 끊어 둔 이유는 테스트다 — 통합 테스트가 실제 구글을 부르면 유료 호출이
 * 발생하고 결과도 시점마다 달라진다.
 */
public interface PlacesClient {

    /** 도시 단위 검색(S-03 목적지). 타임존·통화를 확정하려고 행정구역만 고른다. */
    List<PlaceResult> searchCities(String query);

    /**
     * 일반 장소 검색(S-06).
     *
     * @param bias 목적지 좌표. 있으면 그 주변을 우선한다(§1.5). null이면 편향 없음
     */
    List<PlaceResult> searchPlaces(String query, Coordinates bias);

    /** 장소 상세(영업시간·전화번호·사진 리소스 이름). */
    Optional<PlaceResult> fetchDetails(String googlePlaceId);

    /**
     * 사진 바이트를 받는다.
     *
     * <p>구글이 주는 것은 리소스 이름뿐이고, 이미지 URL은 <b>만료된다</b>. 그래서 DB에 URL을
     * 넣어 둘 수 없고, 받아서 우리 저장소에 넣는 수밖에 없다.
     *
     * @return 실패하면 비어 있다 — 사진이 없다고 장소를 못 쓰게 만들지 않는다
     */
    Optional<byte[]> fetchPhoto(String photoName);

    record Coordinates(BigDecimal lat, BigDecimal lng) {
    }
}
