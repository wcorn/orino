package ds.project.orino.planner.dataset.dto;

import ds.project.orino.domain.planner.dataset.entity.Dataset;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 데이터셋 메타 응답.
 *
 * <p>{@code summaries}는 <b>푸터 요약 값</b>이 앉는 자리다 — {@code summary} 함수가 설정된 열의
 * key → 계산된 값(문자열). 함수(무엇을 계산할지)는 {@link DatasetColumn#summary()}에, <b>계산된
 * 값</b>은 여기에 둔다(값은 데이터에 따라 매번 바뀌므로 열 메타와 분리).
 *
 * <p>이 필드의 <b>형태만</b> 먼저 고정한다(#907 푸터 표면). 지금은 요약 함수가 설정된 열마다
 * 값이 {@code null}이다 — 집계 계산은 후속(#908)에서 채운다. FE는 값이 null이면 placeholder로
 * 그린다.
 */
public record DatasetResponse(
        Long id,
        List<DatasetColumn> columns,
        int rowCount,
        Map<String, String> summaries
) {
    public static DatasetResponse of(Dataset dataset, List<DatasetColumn> columns) {
        // 요약 함수가 설정된 열만 key로 담는다. 값은 아직 null(집계는 #908). null 값을 담아야 해서
        // Collectors.toMap 대신 직접 넣는다.
        Map<String, String> summaries = new LinkedHashMap<>();
        for (DatasetColumn column : columns) {
            if (column.summary() != null) {
                summaries.put(column.key(), null);
            }
        }
        return new DatasetResponse(dataset.getId(), columns, dataset.getRowCount(), summaries);
    }
}
