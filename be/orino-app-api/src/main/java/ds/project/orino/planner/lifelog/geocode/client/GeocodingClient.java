package ds.project.orino.planner.lifelog.geocode.client;

import ds.project.orino.planner.lifelog.geocode.GeocodePlace;

import java.util.List;
import java.util.Optional;

/**
 * 지오코딩 공급자 추상화. 인터페이스로 둬서 테스트가 외부 네트워크(Nominatim)에 의존하지 않고
 * 스텁으로 대체할 수 있게 한다. 캐시·정책(rate limit)은 서비스/구현체의 몫이다.
 */
public interface GeocodingClient {

    /** 좌표 → 장소명. 결과가 없으면 빈 Optional. 호출 실패는 예외를 던진다. */
    Optional<GeocodePlace> reverse(double lat, double lng);

    /** 검색어 → 후보 장소(최대 {@code limit}개). 결과 없음은 빈 리스트, 호출 실패는 예외. */
    List<GeocodePlace> search(String query, int limit);
}
