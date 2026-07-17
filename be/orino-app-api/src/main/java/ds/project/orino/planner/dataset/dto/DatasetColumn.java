package ds.project.orino.planner.dataset.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 데이터셋 열 메타. key는 안정 식별자, label은 표시명.
 *
 * <p>{@code width}는 표시 너비(px)이며 <b>nullable</b>이다. null이면 클라이언트가 기본 폭으로
 * 그린다(기존 동작). 덕분에 width가 없는 기존 columns_json도 그대로 읽히고 마이그레이션이 필요 없다.
 * 직렬화 시 null은 빼서 columns_json에 빈 항목이 쌓이지 않게 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetColumn(
        String key,
        String label,
        @Min(value = MIN_WIDTH, message = "width는 " + MIN_WIDTH + " 이상이어야 합니다.")
        @Max(value = MAX_WIDTH, message = "width는 " + MAX_WIDTH + " 이하여야 합니다.")
        Integer width
) {
    /** 열 너비 하한(px). 이보다 좁으면 값이 사실상 안 보인다. */
    public static final int MIN_WIDTH = 60;
    /** 열 너비 상한(px). 한 열이 표 전체를 밀어내는 것을 막는다. */
    public static final int MAX_WIDTH = 800;

    /** 너비 없이 만든다(기본 폭). */
    public DatasetColumn(String key, String label) {
        this(key, label, null);
    }

    /** key/width는 두고 label만 바꾼 새 열 메타. */
    public DatasetColumn withLabel(String label) {
        return new DatasetColumn(key, label, width);
    }

    /** key/label은 두고 width만 바꾼 새 열 메타. */
    public DatasetColumn withWidth(Integer width) {
        return new DatasetColumn(key, label, width);
    }
}
