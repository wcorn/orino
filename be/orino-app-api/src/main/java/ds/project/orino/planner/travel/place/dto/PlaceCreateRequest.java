package ds.project.orino.planner.travel.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 직접 입력한 장소(S-06 "검색 결과가 없어요"). 구글에 없는 곳도 일정에 넣을 수 있어야 한다.
 */
public record PlaceCreateRequest(
        @NotBlank(message = "장소 이름을 입력해 주세요.")
        @Size(max = 200, message = "장소 이름은 200자를 넘을 수 없습니다.")
        String name,

        @Size(max = 500, message = "주소는 500자를 넘을 수 없습니다.")
        String address,

        BigDecimal lat,
        BigDecimal lng
) {
}
