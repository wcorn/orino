package ds.project.orino.planner.google.calendar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Google 이벤트의 {@code extendedProperties}. orino는 {@code private} 영역만 사용한다.
 *
 * <p>{@code private}는 Java 예약어라 필드명은 {@code privateProperties}, JSON 키는 {@code private}로 매핑한다.
 * 쓰기 시 null 영역은 직렬화에서 제외하고, 읽기 시 알 수 없는 영역(shared 등)은 무시한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleExtendedProperties(
        @JsonProperty("private") Map<String, String> privateProperties
) {

    public static GoogleExtendedProperties ofPrivate(Map<String, String> privateProperties) {
        return new GoogleExtendedProperties(privateProperties);
    }
}
