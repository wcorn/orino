package ds.project.orino.planner.travel.route.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 직선거리. 이동수단 판정(1.5km)과 Routes 실패 시 대체값 둘 다 이 값에 걸려 있다.
 */
class HaversineTest {

    private static int between(String lat1, String lng1, String lat2, String lng2) {
        return Haversine.distanceM(new BigDecimal(lat1), new BigDecimal(lng1),
                new BigDecimal(lat2), new BigDecimal(lng2));
    }

    @Test
    @DisplayName("같은 지점은 0m")
    void samePointIsZero() {
        assertThat(between("35.7147", "139.7966", "35.7147", "139.7966")).isZero();
    }

    @Test
    @DisplayName("센소지 → 도쿄 스카이트리는 약 1.2km (실제 직선 1.2km)")
    void shortDistanceWithinTokyo() {
        // 도보 판정(1.5km)이 갈리는 구간이라 여기가 틀리면 수단이 통째로 바뀐다.
        int meters = between("35.7147", "139.7966", "35.7101", "139.8107");
        assertThat(meters).isBetween(1_200, 1_400);
    }

    @Test
    @DisplayName("서울 → 도쿄는 약 1,160km")
    void longDistanceAcrossCountries() {
        int meters = between("37.5665", "126.9780", "35.6762", "139.6503");
        assertThat(meters).isBetween(1_140_000, 1_180_000);
    }

    @Test
    @DisplayName("방향이 반대여도 거리는 같다")
    void isSymmetric() {
        int forward = between("35.7147", "139.7966", "35.6762", "139.6503");
        int backward = between("35.6762", "139.6503", "35.7147", "139.7966");
        assertThat(forward).isEqualTo(backward);
    }
}
