package ds.project.orino.planner.dataset.dto;

import ds.project.orino.domain.planner.dataset.entity.Dataset;

import java.util.List;
import java.util.Map;

/**
 * 데이터셋 메타 응답.
 *
 * <p>{@code summaries}는 <b>푸터 요약 값</b>이 앉는 자리다 — {@code summary} 함수가 설정된 열의
 * key → 계산된 값(문자열). 함수(무엇을 계산할지)는 {@link DatasetColumn#summary()}에, <b>계산된
 * 값</b>은 여기에 둔다(값은 데이터에 따라 매번 바뀌므로 열 메타와 분리).
 *
 * <p>{@code summaries}엔 요약 함수가 설정된 열의 key → 계산된 값이 담긴다(#908). 계산은
 * 서비스(엔진 접근이 있는 곳)에서 하고 여기로 넘겨받는다 — 값은 데이터에 따라 매번 바뀌므로
 * 열 메타와 분리한다. 요약이 없으면 빈 맵이다.
 */
public record DatasetResponse(
        Long id,
        String name,
        List<DatasetColumn> columns,
        int rowCount,
        Map<String, String> summaries
) {
    public static DatasetResponse of(Dataset dataset, List<DatasetColumn> columns,
                                     Map<String, String> summaries) {
        return new DatasetResponse(dataset.getId(), dataset.getName(), columns,
                dataset.getRowCount(), summaries);
    }
}
